package com.mgrud.github.proxy.gitproxycore.domain.control.client;

import com.mgrud.github.proxy.gitproxycore.domain.control.GithubBranchWrapper;
import com.mgrud.github.proxy.gitproxycore.domain.control.external.dto.GithubBranchDTO;
import com.mgrud.github.proxy.gitproxycore.domain.control.external.dto.GithubRepositoryDTO;
import com.mgrud.github.proxy.gitproxycore.infrastructure.thread.AsynchronousQueryUtil;
import com.mgrud.github.proxy.gitproxycore.infrastructure.thread.ThreadPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class GithubApiClient {
    private final String githubBaseUrl;
    private static final String GITHUB_GET_USER_REPOSITORIES_URL = "/users/%s/repos";
    private static final String GITHUB_GET_USER_REPOSITORIES_BRANCHES_URL = "/repos/%s/%s/branches";


    private final RestTemplate restTemplate;
    private final ThreadPoolService threadPoolService;
    private final UserRepositoryErrorHandler userRepositoryErrorHandler;

    public GithubApiClient(RestTemplateBuilder restTemplateBuilder, ThreadPoolService threadPoolService,
                           UserRepositoryErrorHandler userRepositoryErrorHandler, @Value("${base.url.github}") String githubBaseUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.userRepositoryErrorHandler = userRepositoryErrorHandler;
        this.threadPoolService = threadPoolService;
        this.githubBaseUrl = githubBaseUrl;
    }

    public Collection<GithubRepositoryDTO> getUserRepositories(String userName) {
        restTemplate.setErrorHandler(userRepositoryErrorHandler);
        return Arrays.asList(restTemplate.getForObject(String.format(githubBaseUrl + GITHUB_GET_USER_REPOSITORIES_URL, userName), GithubRepositoryDTO[].class));
    }

    public Map<String, List<GithubBranchWrapper>> getRepositoryBranches(Collection<GithubRepositoryDTO> repositoryDTOs) {

        Collection<CompletableFuture<Collection<GithubBranchWrapper>>> result = repositoryDTOs.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> getRepositoryBranches(url), threadPoolService.getExecutorService()))
                .collect(Collectors.toList());

        return AsynchronousQueryUtil.getFutureQueriesResult(result, "Could not query repositories branches").stream()
                .collect(Collectors.groupingBy(GithubBranchWrapper::getRepositoryName, Collectors.toList()));
    }

    private Collection<GithubBranchWrapper> getRepositoryBranches(GithubRepositoryDTO repositoryDTO) {
        String branchesURL = String.format(
                githubBaseUrl + GITHUB_GET_USER_REPOSITORIES_BRANCHES_URL, repositoryDTO.getOwner().getLogin(), repositoryDTO.getName());
        Collection<GithubBranchDTO> branches = Arrays.asList(restTemplate.getForObject(branchesURL, GithubBranchDTO[].class));

        return branches.stream()
                .map(branch -> mapToGithubBranchWrapper(branch, repositoryDTO.getName()))
                .collect(Collectors.toList());
    }

    private GithubBranchWrapper mapToGithubBranchWrapper(GithubBranchDTO branchDTO, String repositoryName) {
        return GithubBranchWrapper.builder()
                .branch(branchDTO)
                .repositoryName(repositoryName)
                .build();
    }


}
/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.seed_node;

import bisq.bonded_roles.BondedRolesService;
import bisq.common.observable.Pin;
import bisq.identity.IdentityService;
import bisq.java_se.application.JavaSeApplicationService;
import bisq.network.NetworkService;
import bisq.network.NetworkServiceConfig;
import bisq.security.SecurityService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Creates domain specific options from program arguments and application options.
 * Creates domain instance with options and optional dependency to other domain objects.
 * Initializes the domain instances according to the requirements of their dependencies either in sequence
 * or in parallel.
 * Provides the complete setup instances to other clients (Api)
 */
@Getter
@Slf4j
public class SeedNodeApplicationService extends JavaSeApplicationService {
    protected final NetworkService networkService;
    protected final IdentityService identityService;
    protected final SecurityService securityService;
    private final SeedNodeService seedNodeService;
    private final BondedRolesService bondedRolesService;
    @Nullable
    private Pin difficultyAdjustmentServicePin;

    public SeedNodeApplicationService(String[] args) {
        super("seed_node", args);

        securityService = new SecurityService(persistenceService, SecurityService.Config.from(getConfig("security")));

        NetworkServiceConfig networkServiceConfig = NetworkServiceConfig.from(config.getBaseDir(),
                getConfig("network"));
        networkService = new NetworkService(networkServiceConfig,
                persistenceService,
                securityService.getKeyBundleService(),
                securityService.getHashCashProofOfWorkService(),
                securityService.getEquihashProofOfWorkService(),
                memoryReportService);

        identityService = new IdentityService(persistenceService,
                securityService.getKeyBundleService(),
                networkService);

        bondedRolesService = new BondedRolesService(BondedRolesService.Config.from(getConfig("bondedRoles")),
                persistenceService,
                networkService);

        Optional<SeedNodeService.Config> seedNodeConfig = hasConfig("seedNode") ? Optional.of(SeedNodeService.Config.from(getConfig("seedNode"))) : Optional.empty();
        seedNodeService = new SeedNodeService(seedNodeConfig, networkService, identityService, securityService.getKeyBundleService());
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return memoryReportService.initialize()
                .thenCompose(result -> securityService.initialize())
                .thenCompose(result -> networkService.initialize())
                .thenCompose(result -> identityService.initialize())
                .thenCompose(result -> bondedRolesService.initialize())
                .thenCompose(result -> seedNodeService.initialize())
                .orTimeout(5, TimeUnit.MINUTES)
                .whenComplete((success, throwable) -> {
                    if (success) {
                        difficultyAdjustmentServicePin = bondedRolesService.getDifficultyAdjustmentService().getMostRecentValueOrDefault().addObserver(mostRecentValueOrDefault ->
                                networkService.getNetworkLoadServices().forEach(networkLoadService ->
                                        networkLoadService.setDifficultyAdjustmentFactor(mostRecentValueOrDefault)));
                        log.info("SeedNodeApplicationService initialized");
                    } else {
                        log.error("Initializing SeedNodeApplicationService failed", throwable);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        log.info("shutdown");

        if (difficultyAdjustmentServicePin != null) {
            difficultyAdjustmentServicePin.unbind();
            difficultyAdjustmentServicePin = null;
        }

        // We shut down services in opposite order as they are initialized
        return supplyAsync(() -> seedNodeService.shutdown()
                .thenCompose(result -> bondedRolesService.shutdown())
                .thenCompose(result -> identityService.shutdown())
                .thenCompose(result -> networkService.shutdown())
                .thenCompose(result -> securityService.shutdown())
                .thenCompose(result -> memoryReportService.shutdown())
                .orTimeout(10, TimeUnit.SECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error at shutdown", throwable);
                        return false;
                    } else if (!result) {
                        log.error("Shutdown resulted with false");
                        return false;
                    }
                    return true;
                })
                .join());
    }
}

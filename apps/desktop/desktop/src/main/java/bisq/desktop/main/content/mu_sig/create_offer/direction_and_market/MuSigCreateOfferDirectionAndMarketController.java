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

package bisq.desktop.main.content.mu_sig.create_offer.direction_and_market;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.currency.Market;
import bisq.common.currency.MarketRepository;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.utils.KeyHandlerUtil;
import bisq.desktop.common.view.Controller;
import bisq.desktop.navigation.NavigationTarget;
import bisq.mu_sig.MuSigService;
import bisq.offer.Direction;
import bisq.user.identity.UserIdentityService;
import javafx.beans.property.ReadOnlyObjectProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class MuSigCreateOfferDirectionAndMarketController implements Controller {
    private final MuSigCreateOfferDirectionAndMarketModel model;
    @Getter
    private final MuSigCreateOfferDirectionAndMarketView view;
    private final Runnable onNextHandler;
    private final Consumer<Boolean> navigationButtonsVisibleHandler;
    private final Consumer<NavigationTarget> closeAndNavigateToHandler;
    private final UserIdentityService userIdentityService;
    private final MarketPriceService marketPriceService;
    private final MuSigService muSigService;
    private Subscription searchTextPin;

    public MuSigCreateOfferDirectionAndMarketController(ServiceProvider serviceProvider,
                                                        Runnable onNextHandler,
                                                        Consumer<Boolean> navigationButtonsVisibleHandler,
                                                        Consumer<NavigationTarget> closeAndNavigateToHandler) {
        this.onNextHandler = onNextHandler;
        this.navigationButtonsVisibleHandler = navigationButtonsVisibleHandler;
        this.closeAndNavigateToHandler = closeAndNavigateToHandler;
        userIdentityService = serviceProvider.getUserService().getUserIdentityService();
        marketPriceService = serviceProvider.getBondedRolesService().getMarketPriceService();
        muSigService = serviceProvider.getMuSigService();

        model = new MuSigCreateOfferDirectionAndMarketModel();
        view = new MuSigCreateOfferDirectionAndMarketView(model, this);
        setDirection(Direction.BUY);
        applyShowReputationInfo();
    }

    public void setMarket(Market market) {
        model.getSelectedMarket().set(market);
    }

    public ReadOnlyObjectProperty<Direction> getDirection() {
        return model.getDirection();
    }

    public ReadOnlyObjectProperty<Market> getMarket() {
        return model.getSelectedMarket();
    }

    public void reset() {
        model.reset();
    }

    @Override
    public void onActivate() {
        setDirection(Direction.BUY);
        applyShowReputationInfo();

        model.getSearchText().set("");

        model.getListItems().setAll(MarketRepository.getAllFiatMarkets().stream()
                //.filter(market -> marketPriceService.getMarketPriceByCurrencyMap().containsKey(market))
                .map(market -> {
                    long numOffersInMarket = muSigService.getOffers().stream()
                            .filter(offer -> {
                                Market offerMarket = offer.getMarket();

                                // TODO: Needs to be dynamic according to base market
                                // for now we just assume Btc.
                                boolean isBaseMarket = offerMarket.isBtcFiatMarket() && offerMarket.getBaseCurrencyCode().equals("BTC");
                                boolean isQuoteMarket = offerMarket.getQuoteCurrencyCode().equals(market.getQuoteCurrencyCode());
                                return isBaseMarket && isQuoteMarket;
                            })
                            .count();
                    MuSigCreateOfferDirectionAndMarketView.ListItem item = new MuSigCreateOfferDirectionAndMarketView.ListItem(market, numOffersInMarket);
                    if (market.equals(model.getSelectedMarket().get())) {
                        model.getSelectedMarketListItem().set(item);
                    }
                    return item;
                })
                .collect(Collectors.toList()));

        searchTextPin = EasyBind.subscribe(model.getSearchText(), searchText -> {
            if (searchText == null || searchText.isEmpty()) {
                model.getFilteredList().setPredicate(item -> true);
            } else {
                String search = searchText.toLowerCase();
                model.getFilteredList().setPredicate(item ->
                        item != null &&
                                (item.getQuoteCurrencyDisplayName().toLowerCase().contains(search) ||
                                        item.getMarket().getQuoteCurrencyDisplayName().toLowerCase().contains(search))
                );
            }
        });
    }

    @Override
    public void onDeactivate() {
        view.getRoot().setOnKeyPressed(null);
        searchTextPin.unsubscribe();
    }

    public boolean validate() {
        if (model.getDirection().get() == Direction.SELL && !model.isAllowedToCreateSellOffer()) {
            showReputationInfoOverlay();
            return false;
        }

        return true;
    }

    void onSelectDirection(Direction direction) {
        setDirection(direction);
        applyShowReputationInfo();
        if (!model.getShowReputationInfo().get()) {
            onNextHandler.run();
        }
    }

    void onCloseReputationInfo() {
        setDirection(Direction.BUY);
        applyShowReputationInfo();
    }

    void onBuildReputation() {
        closeAndNavigateToHandler.accept(NavigationTarget.BUILD_REPUTATION);
    }

    void onMarketListItemClicked(MuSigCreateOfferDirectionAndMarketView.ListItem item) {
        if (item == null) {
            return;
        }
        if (item.equals(model.getSelectedMarketListItem().get())) {
            onNextHandler.run();
        }
        model.getSelectedMarketListItem().set(item);
        model.getSelectedMarket().set(item.getMarket());
        muSigService.getMuSigSelectedMarket().set(item.getMarket());
    }

    private void setDirection(Direction direction) {
        model.getDirection().set(direction);
    }

    private void applyShowReputationInfo() {
        if (model.getDirection().get() == Direction.BUY) {
            model.getShowReputationInfo().set(false);
            navigationButtonsVisibleHandler.accept(true);
            return;
        }

        // Sell offer
        if (!model.isAllowedToCreateSellOffer()) {
            showReputationInfoOverlay();
        } else {
            view.getRoot().setOnKeyPressed(null);
        }
    }

    private void showReputationInfoOverlay() {
        navigationButtonsVisibleHandler.accept(false);
        model.getShowReputationInfo().set(true);
        view.getRoot().setOnKeyPressed(keyEvent -> {
            KeyHandlerUtil.handleEnterKeyEvent(keyEvent, () -> {
            });
            KeyHandlerUtil.handleEscapeKeyEvent(keyEvent, this::onCloseReputationInfo);
        });
    }
}

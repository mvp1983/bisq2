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

package bisq.desktop.main.content.mu_sig.offerbook;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.currency.Market;
import bisq.common.currency.MarketRepository;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.CollectionObserver;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.Navigation;
import bisq.desktop.components.overlay.Popup;
import bisq.desktop.main.content.mu_sig.create_offer.MuSigCreateOfferController;
import bisq.desktop.main.content.mu_sig.take_offer.MuSigTakeOfferController;
import bisq.desktop.navigation.NavigationTarget;
import bisq.i18n.Res;
import bisq.identity.IdentityService;
import bisq.mu_sig.MuSigService;
import bisq.offer.mu_sig.MuSigOffer;
import bisq.presentation.formatters.PriceFormatter;
import bisq.settings.CookieKey;
import bisq.settings.FavouriteMarketsService;
import bisq.settings.SettingsService;
import bisq.user.banned.BannedUserService;
import bisq.user.banned.RateLimitExceededException;
import bisq.user.banned.UserProfileBannedException;
import bisq.user.profile.UserProfileService;
import lombok.Getter;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class MuSigOfferbookController implements Controller {
    @Getter
    private final MuSigOfferbookView view;
    private final MuSigOfferbookModel model;
    private final MuSigService muSigService;
    private final MarketPriceService marketPriceService;
    private final UserProfileService userProfileService;
    private final SettingsService settingsService;
    private final IdentityService identityService;
    private final BannedUserService bannedUserService;
    private final FavouriteMarketsService favouriteMarketsService;
    private Pin offersPin, selectedMarketPin;
    private Subscription selectedMarketItemPin;

    public MuSigOfferbookController(ServiceProvider serviceProvider) {
        muSigService = serviceProvider.getMuSigService();
        marketPriceService = serviceProvider.getBondedRolesService().getMarketPriceService();
        userProfileService = serviceProvider.getUserService().getUserProfileService();
        identityService = serviceProvider.getIdentityService();
        settingsService = serviceProvider.getSettingsService();
        bannedUserService = serviceProvider.getUserService().getBannedUserService();
        favouriteMarketsService = serviceProvider.getFavouriteMarketsService();

        model = new MuSigOfferbookModel();
        view = new MuSigOfferbookView(model, this);
    }

    @Override
    public void onActivate() {
        List<MarketItem> marketItems = MarketRepository.getAllFiatMarkets().stream()
                .map(market -> new MarketItem(market,
                        favouriteMarketsService,
                        marketPriceService,
                        userProfileService,
                        muSigService))
                .collect(Collectors.toList());
        model.getMarketItems().setAll(marketItems);

        applyInitialSelectedMarket();

        offersPin = muSigService.getObservableOffers().addObserver(new CollectionObserver<>() {
            @Override
            public void add(MuSigOffer muSigOffer) {
                UIThread.run(() -> {
                    String offerId = muSigOffer.getId();
                    if (/*isExpectedMarket(muSigOffer.getMarket()) && */!model.getMuSigOfferIds().contains(offerId)) {
                        model.getMuSigOfferListItems().add(new MuSigOfferListItem(muSigOffer, marketPriceService, userProfileService, identityService));
                        model.getMuSigOfferIds().add(offerId);
                        //updatePredicate();
                    }
                });
            }

            @Override
            public void remove(Object element) {
                if (element instanceof MuSigOffer muSigOffer) {
                    UIThread.run(() -> {
                        String offerId = muSigOffer.getId();
                        Optional<MuSigOfferListItem> toRemove = model.getMuSigOfferListItems().stream()
                                .filter(item -> item.getOffer().getId().equals(offerId))
                                .findAny();
                        toRemove.ifPresent(offer -> {
                            model.getMuSigOfferListItems().remove(offer);
                            model.getMuSigOfferIds().remove(offerId);
                        });
                    });
                }
            }

            @Override
            public void clear() {
                UIThread.run(() -> {
                    model.getMuSigOfferListItems().clear();
                    model.getMuSigOfferIds().clear();
                });
            }
        });

        selectedMarketItemPin = EasyBind.subscribe(model.getSelectedMarketItem(), selectedMarketItem -> {
            if (selectedMarketItem != null) {
                updateFilteredMuSigOfferListItemsPredicate();
                updateMarketData(selectedMarketItem);
                muSigService.getMuSigSelectedMarket().set(selectedMarketItem.getMarket());
            }
        });
        selectedMarketPin = muSigService.getMuSigSelectedMarket().addObserver(market -> {
            if (market != null) {
                model.getMarketItems().stream()
                        .filter(item -> item.getMarket().equals(market))
                        .findAny()
                        .ifPresent(item -> model.getSelectedMarketItem().set(item));
            }
        });
    }

    @Override
    public void onDeactivate() {
        offersPin.unbind();
        model.getMuSigOfferListItems().forEach(MuSigOfferListItem::dispose);
        model.getMuSigOfferListItems().clear();
        model.getMuSigOfferIds().clear();

        selectedMarketItemPin.unsubscribe();
        selectedMarketPin.unbind();
    }

    void onSelectMarketItem(MarketItem marketItem) {
        if (marketItem == null) {
            model.getSelectedMarketItem().set(null);
            maybeSelectFirst();
        } else {
            model.getSelectedMarketItem().set(marketItem);
            Market market = marketItem.getMarket();
            settingsService.setSelectedMarket(market);
            settingsService.setCookie(getSelectedMarketCookieKey(), market.getMarketCodes());
        }
    }

    void onCreateOffer() {
        MarketItem marketItem = model.getSelectedMarketItem().get();
        checkArgument(marketItem != null, "No selected market item");
        Navigation.navigateTo(NavigationTarget.MU_SIG_CREATE_OFFER, new MuSigCreateOfferController.InitData(marketItem.getMarket()));
    }

    void onTakeOffer(MuSigOffer offer) {
        Navigation.navigateTo(NavigationTarget.MU_SIG_TAKE_OFFER, new MuSigTakeOfferController.InitData(offer));
    }

    void onRemoveOffer(MuSigOffer muSigOffer) {
        new Popup().warning(Res.get("muSig.offerbook.removeOffer.confirmation"))
                .actionButtonText(Res.get("confirmation.yes"))
                .onAction(() -> doRemoveOffer(muSigOffer))
                .closeButtonText(Res.get("confirmation.no"))
                .show();
    }

    private void doRemoveOffer(MuSigOffer muSigOffer) {
        try {
            muSigService.removeOffer(muSigOffer);
        } catch (UserProfileBannedException e) {
            UIThread.run(() -> {
                // We do not inform banned users about being banned
            });
        } catch (RateLimitExceededException e) {
            UIThread.run(() -> {
                new Popup().warning(Res.get("muSig.offerbook.rateLimitsExceeded.removeOffer.warning")).show();
            });
        }
    }

    private void maybeSelectFirst() {
        MarketItem firstMarketItem = getFirstMarketItem();
        if (firstMarketItem != null) {
            model.getSelectedMarketItem().set(firstMarketItem);
        }
    }

    private MarketItem getFirstMarketItem() {
        return !model.getSortedMarketItems().isEmpty() ? model.getSortedMarketItems().get(0) : null;
    }

    private CookieKey getSelectedMarketCookieKey() {
        // TODO: Update this according to selected base market
        return CookieKey.MU_SIG_OFFERBOOK_SELECTED_BTC_MARKET;
    }

    private void applyInitialSelectedMarket() {
        Optional<Market> selectedMarket = settingsService.getCookie().asString(getSelectedMarketCookieKey())
                .flatMap(MarketRepository::findAnyMarketByMarketCodes)
                .filter(this::isExpectedMarket);

        selectedMarket.flatMap(market -> model.getMarketItems().stream()
                .filter(item -> item.getMarket().equals(market))
                .findAny())
                .ifPresentOrElse(
                        item -> model.getSelectedMarketItem().set(item),
                        () -> model.getSelectedMarketItem().set(getFirstMarketItem())
                );
    }

    private boolean isExpectedMarket(Market market) {
        // TODO: Here we need to use de base market selection instead.
        return market.isBtcFiatMarket() && market.getBaseCurrencyCode().equals("BTC");
    }

    private void updateFilteredMuSigOfferListItemsPredicate() {
        model.getFilteredMuSigOfferListItems().setPredicate(null);
        model.getFilteredMuSigOfferListItems().setPredicate(item ->
            model.getSelectedMarketItem().get().getMarket().equals(item.getMarket()));
    }

    private void updateMarketData(MarketItem selectedMarketItem) {
        if (selectedMarketItem != null) {
            Market selectedMarket = selectedMarketItem.getMarket();
            if (selectedMarket != null) {
                model.getMarketTitle().set(Res.get("muSig.offerbook.marketHeader.title", selectedMarket.getMarketDisplayName()));
                model.getMarketDescription().set(selectedMarket.getMarketCodes());
                marketPriceService
                        .findMarketPrice(selectedMarket)
                        .ifPresentOrElse(
                                marketPrice -> model.getMarketPrice().set(PriceFormatter.format(marketPrice.getPriceQuote(), true)),
                                () -> model.getMarketPrice().set(""));
                model.getBaseCodeTitle().set(selectedMarket.getBaseCurrencyCode());
                model.getQuoteCodeTitle().set(selectedMarket.getQuoteCurrencyCode());
                model.getPriceTitle().set(Res.get("muSig.offerbook.table.header.price", selectedMarket.getMarketCodes()).toUpperCase());
                model.getMarketIconId().set(selectedMarket.getBaseCurrencyCode());
            }
        } else {
            model.getMarketTitle().set("");
            model.getMarketDescription().set("");
            model.getMarketPrice().set("");
        }
    }
}

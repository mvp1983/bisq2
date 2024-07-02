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

package bisq.desktop.main.content.bisq_easy.take_offer.payment_method;

import bisq.desktop.ServiceProvider;
import bisq.desktop.common.view.Controller;
import bisq.desktop.components.overlay.Popup;
import bisq.i18n.Res;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.offer.payment_method.BitcoinPaymentMethodSpec;
import bisq.offer.payment_method.FiatPaymentMethodSpec;
import bisq.offer.payment_method.PaymentMethodSpec;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;

@Slf4j
public class TakeOfferPaymentController implements Controller {
    private final TakeOfferPaymentModel model;
    @Getter
    private final TakeOfferPaymentView view;

    public TakeOfferPaymentController(ServiceProvider serviceProvider) {
        model = new TakeOfferPaymentModel();
        view = new TakeOfferPaymentView(model, this);
    }

    public void init(BisqEasyOffer bisqEasyOffer) {
        model.getOfferedBitcoinPaymentMethodSpecs().setAll(bisqEasyOffer.getBaseSidePaymentMethodSpecs());
        model.getOfferedFiatPaymentMethodSpecs().setAll(bisqEasyOffer.getQuoteSidePaymentMethodSpecs());
        model.setBitcoinHeadline(bisqEasyOffer.getTakersDirection().isBuy() ?
                Res.get("bisqEasy.takeOffer.paymentMethod.headline.bitcoin.buyer") :
                Res.get("bisqEasy.takeOffer.paymentMethod.headline.bitcoin.seller"));
        model.setFiatHeadline(bisqEasyOffer.getTakersDirection().isBuy() ?
                Res.get("bisqEasy.takeOffer.paymentMethod.headline.fiat.buyer", bisqEasyOffer.getMarket().getQuoteCurrencyCode()) :
                Res.get("bisqEasy.takeOffer.paymentMethod.headline.fiat.seller", bisqEasyOffer.getMarket().getQuoteCurrencyCode()));
    }

    public ReadOnlyObjectProperty<BitcoinPaymentMethodSpec> getSelectedBitcoinPaymentMethodSpec() {
        return model.getSelectedBitcoinPaymentMethodSpec();
    }

    public ReadOnlyObjectProperty<FiatPaymentMethodSpec> getSelectedFiatPaymentMethodSpec() {
        return model.getSelectedFiatPaymentMethodSpec();
    }

    public boolean isValid() {
        return model.getSelectedBitcoinPaymentMethodSpec().get() != null && model.getSelectedFiatPaymentMethodSpec().get() != null;
    }

    public void handleInvalidInput() {
        new Popup().invalid(Res.get("bisqEasy.takeOffer.paymentMethod.noneSelected"))
                .owner((Region) view.getRoot().getParent().getParent())
                .show();
    }

    @Override
    public void onActivate() {
        model.getSortedBitcoinPaymentMethodSpecs().setComparator(Comparator.comparing(e -> e.getPaymentMethod().getPaymentRail()));
        model.getSortedFiatPaymentMethodSpecs().setComparator(Comparator.comparing(PaymentMethodSpec::getShortDisplayString));
        model.setBitcoinMethodVisible(model.getOfferedBitcoinPaymentMethodSpecs().size() > 1);
        model.setFiatMethodVisible(model.getOfferedFiatPaymentMethodSpecs().size() > 1);
        if (model.getOfferedBitcoinPaymentMethodSpecs().size() == 1) {
            model.getSelectedBitcoinPaymentMethodSpec().set(model.getOfferedBitcoinPaymentMethodSpecs().get(0));
        }
        if (model.getOfferedFiatPaymentMethodSpecs().size() == 1) {
            model.getSelectedFiatPaymentMethodSpec().set(model.getOfferedFiatPaymentMethodSpecs().get(0));
        }
    }

    @Override
    public void onDeactivate() {
    }

    void onToggleFiatPaymentMethod(FiatPaymentMethodSpec spec, boolean selected) {
        if (selected && spec != null) {
            model.getSelectedFiatPaymentMethodSpec().set(spec);
        } else {
            model.getSelectedFiatPaymentMethodSpec().set(null);
        }
    }

    void onToggleBitcoinPaymentMethod(BitcoinPaymentMethodSpec spec, boolean selected) {
        if (selected && spec != null) {
            model.getSelectedBitcoinPaymentMethodSpec().set(spec);
        } else {
            model.getSelectedBitcoinPaymentMethodSpec().set(null);
        }
    }
}

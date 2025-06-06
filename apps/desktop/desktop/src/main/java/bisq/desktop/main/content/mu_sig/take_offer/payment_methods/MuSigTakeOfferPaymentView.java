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

package bisq.desktop.main.content.mu_sig.take_offer.payment_methods;

import bisq.account.payment_method.FiatPaymentMethod;
import bisq.desktop.common.utils.GridPaneUtil;
import bisq.desktop.common.utils.ImageUtil;
import bisq.desktop.common.view.View;
import bisq.desktop.components.containers.Spacer;
import bisq.desktop.components.controls.ChipToggleButton;
import bisq.desktop.main.content.bisq_easy.BisqEasyViewUtils;
import bisq.offer.payment_method.FiatPaymentMethodSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MuSigTakeOfferPaymentView extends View<VBox, MuSigTakeOfferPaymentModel, MuSigTakeOfferPaymentController> {
    private static final double MULTIPLE_COLUMN_WIDTH = 21.30;
    private static final double TWO_COLUMN_WIDTH = 20.75;

    private final GridPane fiatGridPane;
    private final ToggleGroup fiatToggleGroup;
    private final Label headlineLabel, subtitleLabel;
    private final VBox fiatVBox;

    public MuSigTakeOfferPaymentView(MuSigTakeOfferPaymentModel model, MuSigTakeOfferPaymentController controller) {
        super(new VBox(), model, controller);

        root.setAlignment(Pos.TOP_CENTER);

        headlineLabel = new Label();
        headlineLabel.getStyleClass().add("bisq-text-headline-2");

        subtitleLabel = new Label();
        subtitleLabel.setTextAlignment(TextAlignment.CENTER);
        subtitleLabel.setAlignment(Pos.CENTER);
        subtitleLabel.getStyleClass().addAll("bisq-text-3");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMaxWidth(600);
        fiatToggleGroup = new ToggleGroup();
        fiatGridPane = GridPaneUtil.getGridPane(10, 10, new Insets(0));
        fiatGridPane.setAlignment(Pos.CENTER);

        fiatVBox = new VBox(25, subtitleLabel, fiatGridPane);
        fiatVBox.setAlignment(Pos.CENTER);

        VBox.setMargin(headlineLabel, new Insets(0, 0, 40, 0));
        VBox.setMargin(fiatGridPane, new Insets(0, 0, 45, 0));
        root.getChildren().addAll(Spacer.fillVBox(), headlineLabel, fiatVBox, Spacer.fillVBox());

        root.setOnMousePressed(e -> root.requestFocus());
    }

    @Override
    protected void onViewAttached() {
        headlineLabel.setText(model.getHeadline());

        fiatVBox.setVisible(model.isFiatMethodVisible());
        fiatVBox.setManaged(model.isFiatMethodVisible());
        if (model.isFiatMethodVisible()) {
            subtitleLabel.setText(model.getSubtitle());
            fiatGridPane.getChildren().clear();
            fiatGridPane.getColumnConstraints().clear();
            int numColumns = model.getSortedFiatPaymentMethodSpecs().size();
            GridPaneUtil.setGridPaneMultiColumnsConstraints(fiatGridPane, numColumns, numColumns == 2 ? TWO_COLUMN_WIDTH : MULTIPLE_COLUMN_WIDTH);
            int col = 0;
            int row = 0;
            for (FiatPaymentMethodSpec spec : model.getSortedFiatPaymentMethodSpecs()) {
                FiatPaymentMethod paymentMethod = spec.getPaymentMethod();
                ChipToggleButton chipToggleButton = new ChipToggleButton(paymentMethod.getShortDisplayString(), fiatToggleGroup);
                Node icon = !paymentMethod.isCustomPaymentMethod()
                        ? ImageUtil.getImageViewById(paymentMethod.getName())
                        : BisqEasyViewUtils.getCustomPaymentMethodIcon(paymentMethod.getDisplayString());
                chipToggleButton.setLeftIcon(icon);
                chipToggleButton.setOnAction(() -> controller.onToggleFiatPaymentMethod(spec, chipToggleButton.isSelected()));
                chipToggleButton.setSelected(spec.equals(model.getSelectedFiatPaymentMethodSpec().get()));
                fiatGridPane.add(chipToggleButton, col++, row);
            }
        }
    }

    @Override
    protected void onViewDetached() {
        fiatGridPane.getChildren().stream()
                .filter(e -> e instanceof ChipToggleButton)
                .map(e -> (ChipToggleButton) e)
                .forEach(chipToggleButton -> chipToggleButton.setOnAction(null));
        fiatGridPane.getChildren().clear();
    }
}

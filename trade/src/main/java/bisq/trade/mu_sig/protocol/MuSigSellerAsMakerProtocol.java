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

package bisq.trade.mu_sig.protocol;

import bisq.common.fsm.FsmErrorEvent;
import bisq.trade.ServiceProvider;
import bisq.trade.mu_sig.MuSigTrade;
import bisq.trade.mu_sig.events.MuSigFsmErrorEventHandler;
import bisq.trade.mu_sig.events.MuSigReportErrorMessageHandler;
import bisq.trade.mu_sig.events.blockchain.MuSigDepositTxConfirmedEvent;
import bisq.trade.mu_sig.events.blockchain.MuSigDepositTxConfirmedEventHandler;
import bisq.trade.mu_sig.events.seller_as_maker.MuSigPaymentReceiptConfirmedEvent;
import bisq.trade.mu_sig.events.seller_as_maker.MuSigPaymentReceiptConfirmedEventHandler;
import bisq.trade.mu_sig.events.seller_as_maker.MuSigSellersCloseTimeoutEvent;
import bisq.trade.mu_sig.events.seller_as_maker.MuSigSellersCloseTimeoutEventHandler;
import bisq.trade.mu_sig.messages.network.MuSigCooperativeClosureMessage_G;
import bisq.trade.mu_sig.messages.network.MuSigPaymentInitiatedMessage_E;
import bisq.trade.mu_sig.messages.network.MuSigReportErrorMessage;
import bisq.trade.mu_sig.messages.network.MuSigSetupTradeMessage_A;
import bisq.trade.mu_sig.messages.network.MuSigSetupTradeMessage_C;
import bisq.trade.mu_sig.messages.network.handler.seller_as_maker.MuSigCooperativeClosureMessage_G_Handler;
import bisq.trade.mu_sig.messages.network.handler.seller_as_maker.MuSigPaymentInitiatedMessage_E_Handler;
import bisq.trade.mu_sig.messages.network.handler.seller_as_maker.MuSigSetupTradeMessage_A_Handler;
import bisq.trade.mu_sig.messages.network.handler.seller_as_maker.MuSigSetupTradeMessage_C_Handler;

import static bisq.trade.mu_sig.protocol.MuSigTradeState.DEPOSIT_TX_CONFIRMED;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.FAILED;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.FAILED_AT_PEER;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.INIT;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_CLOSED_TRADE;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_CONFIRMED_PAYMENT_RECEIPT;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_CREATED_PARTIAL_SIGNATURES_AND_SIGNED_DEPOSIT_TX;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_FORCE_CLOSED_TRADE;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_INITIALIZED_TRADE_AND_CREATED_NONCE_SHARES;
import static bisq.trade.mu_sig.protocol.MuSigTradeState.SELLER_AS_MAKER_RECEIVED_INITIATED_PAYMENT_MESSAGE;


public final class MuSigSellerAsMakerProtocol extends MuSigProtocol {

    public MuSigSellerAsMakerProtocol(ServiceProvider serviceProvider, MuSigTrade model) {
        super(serviceProvider, model);
    }

    @Override
    protected void configErrorHandling() {
        fromAny()
                .on(FsmErrorEvent.class)
                .run(MuSigFsmErrorEventHandler.class)
                .to(FAILED);

        fromAny()
                .on(MuSigReportErrorMessage.class)
                .run(MuSigReportErrorMessageHandler.class)
                .to(FAILED_AT_PEER);
    }

    public void configTransitions() {
        // Setup trade
        from(INIT)
                .on(MuSigSetupTradeMessage_A.class)
                .run(MuSigSetupTradeMessage_A_Handler.class)
                .to(SELLER_AS_MAKER_INITIALIZED_TRADE_AND_CREATED_NONCE_SHARES)
                .then()
                .from(SELLER_AS_MAKER_INITIALIZED_TRADE_AND_CREATED_NONCE_SHARES)
                .on(MuSigSetupTradeMessage_C.class)
                .run(MuSigSetupTradeMessage_C_Handler.class)
                .to(SELLER_AS_MAKER_CREATED_PARTIAL_SIGNATURES_AND_SIGNED_DEPOSIT_TX)

                // Deposit confirmation phase
                .then()
                .from(SELLER_AS_MAKER_CREATED_PARTIAL_SIGNATURES_AND_SIGNED_DEPOSIT_TX)
                .on(MuSigDepositTxConfirmedEvent.class)
                .run(MuSigDepositTxConfirmedEventHandler.class)
                .to(DEPOSIT_TX_CONFIRMED)

                // TODO observe blockchain to know when deposit tx is published or
                //  buyer send informative message after publishing

                // Wait for buyers payment...


                // Settlement
                .then()
                .from(DEPOSIT_TX_CONFIRMED)
                .on(MuSigPaymentInitiatedMessage_E.class)
                .run(MuSigPaymentInitiatedMessage_E_Handler.class)
                .to(SELLER_AS_MAKER_RECEIVED_INITIATED_PAYMENT_MESSAGE)

                .then()
                .from(SELLER_AS_MAKER_RECEIVED_INITIATED_PAYMENT_MESSAGE)
                .on(MuSigPaymentReceiptConfirmedEvent.class)
                .run(MuSigPaymentReceiptConfirmedEventHandler.class)
                .to(SELLER_AS_MAKER_CONFIRMED_PAYMENT_RECEIPT)


                // Close trade
                .then()
                .branch(
                        path("Cooperative closure")
                                .from(SELLER_AS_MAKER_CONFIRMED_PAYMENT_RECEIPT)
                                .on(MuSigCooperativeClosureMessage_G.class)
                                .run(MuSigCooperativeClosureMessage_G_Handler.class)
                                .to(SELLER_AS_MAKER_CLOSED_TRADE),

                        path("Uncooperative closure")
                                .from(SELLER_AS_MAKER_CONFIRMED_PAYMENT_RECEIPT)
                                .on(MuSigSellersCloseTimeoutEvent.class)
                                .run(MuSigSellersCloseTimeoutEventHandler.class)
                                .to(SELLER_AS_MAKER_FORCE_CLOSED_TRADE)
                );
    }
}
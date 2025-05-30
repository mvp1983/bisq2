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

package bisq.trade.bisq_easy.protocol.events;

import bisq.common.util.StringUtils;
import bisq.trade.ServiceProvider;
import bisq.trade.bisq_easy.BisqEasyTrade;
import bisq.trade.bisq_easy.handler.BisqEasyTradeEventHandlerAsMessageSender;
import bisq.trade.bisq_easy.protocol.messages.BisqEasyBtcAddressMessage;

public class BisqEasySendBtcAddressEventHandler extends BisqEasyTradeEventHandlerAsMessageSender<BisqEasyTrade, BisqEasySendBtcAddressEvent> {
    private String bitcoinPaymentData;

    public BisqEasySendBtcAddressEventHandler(ServiceProvider serviceProvider, BisqEasyTrade model) {
        super(serviceProvider, model);
    }

    @Override
    public void process(BisqEasySendBtcAddressEvent event) {
        bitcoinPaymentData = event.getBitcoinPaymentData();
    }

    @Override
    protected void commit() {
        trade.getBitcoinPaymentData().set(bitcoinPaymentData);
    }

    @Override
    protected void sendMessage() {
        send(new BisqEasyBtcAddressMessage(StringUtils.createUid(),
                trade.getId(),
                trade.getProtocolVersion(),
                trade.getMyIdentity().getNetworkId(),
                trade.getPeer().getNetworkId(),
                bitcoinPaymentData,
                trade.getOffer()));
    }
}

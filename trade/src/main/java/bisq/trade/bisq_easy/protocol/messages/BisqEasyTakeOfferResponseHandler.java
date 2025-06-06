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

package bisq.trade.bisq_easy.protocol.messages;

import bisq.contract.ContractService;
import bisq.contract.ContractSignatureData;
import bisq.trade.ServiceProvider;
import bisq.trade.bisq_easy.BisqEasyTrade;
import bisq.trade.bisq_easy.handler.BisqEasyTradeMessageHandler;
import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BisqEasyTakeOfferResponseHandler extends BisqEasyTradeMessageHandler<BisqEasyTrade, BisqEasyTakeOfferResponse> {
    private ContractSignatureData makersContractSignatureData;

    public BisqEasyTakeOfferResponseHandler(ServiceProvider serviceProvider, BisqEasyTrade model) {
        super(serviceProvider, model);
    }

    @Override
    protected void verify(BisqEasyTakeOfferResponse message) {
        ContractSignatureData makersContractSignatureData = message.getContractSignatureData();
        ContractSignatureData takersContractSignatureData = trade.getTaker().getContractSignatureData().get();
        checkArgument(Arrays.equals(makersContractSignatureData.getContractHash(), takersContractSignatureData.getContractHash()),
                "Takers and makers contracts must be the same");

        ContractService contractService = serviceProvider.getContractService();
        try {
            checkArgument(contractService.verifyContractSignature(trade.getContract(), makersContractSignatureData),
                    "Verifying makers contract signature failed");
        } catch (GeneralSecurityException e) {
            log.error("Verifying makers contract signature failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void process(BisqEasyTakeOfferResponse message) {
        makersContractSignatureData = message.getContractSignatureData();
    }

    @Override
    protected void commit() {
        trade.getMaker().getContractSignatureData().set(makersContractSignatureData);
    }
}
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

package bisq.account.payment_method;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class StablecoinPaymentMethodUtil {
    public static Optional<StablecoinPaymentMethod> findPaymentMethod(String name) {
        try {
            StablecoinPaymentRail paymentRail = StablecoinPaymentRail.valueOf(name);
            StablecoinPaymentMethod paymentMethod = StablecoinPaymentMethod.fromPaymentRail(paymentRail);
            return Optional.of(paymentMethod);
        } catch (Throwable e) {
            log.warn("Could not find payment method for name: {}", name, e);
            return Optional.empty();
        }
    }

    public static StablecoinPaymentMethod getPaymentMethod(String name) {
        try {
            StablecoinPaymentRail paymentRail = StablecoinPaymentRail.valueOf(name);
            StablecoinPaymentMethod paymentMethod = StablecoinPaymentMethod.fromPaymentRail(paymentRail);
            if (!paymentMethod.isCustomPaymentMethod()) {
                return paymentMethod;
            }
        } catch (Throwable e) {
            log.warn("Could not get payment method for name: {}", name, e);
        }

        //TODO can be removed once stable coin domain is completed and confirmed that this is not needed
        return StablecoinPaymentMethod.fromCustomName(name);
    }

    public static List<StablecoinPaymentMethod> getPaymentMethods(String currencyCode) {
        return StablecoinPaymentRailUtil.getPaymentRails(currencyCode).stream()
                .map(StablecoinPaymentMethod::fromPaymentRail)
                .collect(Collectors.toList());
    }
}
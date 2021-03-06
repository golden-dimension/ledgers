/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.ledgers.deposit.api.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BinaryOperator;


@Data
public class BalanceBO {
    private AmountBO amount;
    private BalanceTypeBO balanceType;
    private LocalDateTime lastChangeDateTime;
    private LocalDate referenceDate;
    private String lastCommittedTransaction;

    public void updateAmount(BigDecimal amount, BinaryOperator<BigDecimal> functionToApply) {
        BigDecimal newAmount = functionToApply.apply(this.amount.getAmount(), amount);
        this.amount.setAmount(newAmount);
    }

    public boolean isSufficientAmountAvailable(BigDecimal requestedAmount, BigDecimal creditLimit) {
        BigDecimal availableFunds = amount.getAmount().add(creditLimit);
        return Optional.ofNullable(requestedAmount)
                       .map(r -> availableFunds.compareTo(requestedAmount) >= 0)
                       .orElse(false);
    }
}

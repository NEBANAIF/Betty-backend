package com.company.erp.bank;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.payment.Payment;
import com.company.erp.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankResolverTest {

    @Mock private BankRepository bankRepository;

    private BankResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BankResolver(bankRepository);
    }

    private Bank bank(boolean active) {
        Bank bank = new Bank();
        bank.setName("Dashen Bank");
        bank.setAccountNumber("777");
        bank.setActive(active);
        return bank;
    }

    @Test
    void aChosenBankIsLinkedAndItsNameSnapshotted() {
        UUID id = UUID.randomUUID();
        Bank bank = bank(true);
        when(bankRepository.findById(id)).thenReturn(Optional.of(bank));
        Payment payment = new Payment();

        resolver.attach(payment, PaymentMethod.BANK, id, "ignored", "  TX-9 ");

        assertThat(payment.getBank()).isSameAs(bank);
        assertThat(payment.getBankName()).isEqualTo("Dashen Bank");
        assertThat(payment.getAccountReference()).isEqualTo("TX-9");
    }

    @Test
    void aTypedBankNameIsStillAcceptedForOlderClients() {
        Payment payment = new Payment();

        resolver.attach(payment, PaymentMethod.BANK, null, "  Old Bank ", null);

        assertThat(payment.getBank()).isNull();
        assertThat(payment.getBankName()).isEqualTo("Old Bank");
        assertThat(payment.getAccountReference()).isNull();
    }

    @Test
    void aBankPaymentNeedsABank() {
        assertThatThrownBy(() -> resolver.attach(new Payment(), PaymentMethod.BANK, null, "  ", null))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> resolver.attach(new Payment(), PaymentMethod.BANK, null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anInactiveBankIsRefused() {
        UUID id = UUID.randomUUID();
        when(bankRepository.findById(id)).thenReturn(Optional.of(bank(false)));

        assertThatThrownBy(() -> resolver.attach(new Payment(), PaymentMethod.BANK, id, null, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void anUnknownBankIsNotFound() {
        UUID id = UUID.randomUUID();
        when(bankRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.attach(new Payment(), PaymentMethod.BANK, id, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cashAndCreditNeverCarryABank() {
        Payment payment = new Payment();
        payment.setBankName("stale");

        resolver.attach(payment, PaymentMethod.CASH, UUID.randomUUID(), "Some Bank", "R-1");

        assertThat(payment.getBank()).isNull();
        assertThat(payment.getBankName()).isNull();
        assertThat(payment.getAccountReference()).isEqualTo("R-1");
    }
}

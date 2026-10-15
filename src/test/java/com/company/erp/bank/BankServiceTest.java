package com.company.erp.bank;

import com.company.erp.bank.dto.BankRequest;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock private BankRepository bankRepository;

    private BankService service;

    @BeforeEach
    void setUp() {
        service = new BankService(bankRepository);
    }

    private Bank bank(String name, String account, boolean active) {
        Bank bank = new Bank();
        bank.setName(name);
        bank.setAccountNumber(account);
        bank.setActive(active);
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(bank, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return bank;
    }

    @Test
    void aNewBankIsTrimmedAndStartsActive() {
        when(bankRepository.existsByNameIgnoreCaseAndAccountNumber("Awash Bank", "0132000111222")).thenReturn(false);
        when(bankRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new BankRequest("  Awash Bank ", " Bole Store ", " 0132000111222 ", "  "));

        assertThat(response.name()).isEqualTo("Awash Bank");
        assertThat(response.accountNumber()).isEqualTo("0132000111222");
        assertThat(response.accountName()).isEqualTo("Bole Store");
        assertThat(response.notes()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void theSameAccountAtTheSameBankCannotBeRegisteredTwice() {
        when(bankRepository.existsByNameIgnoreCaseAndAccountNumber("awash bank", "0132000111222")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new BankRequest("awash bank", null, "0132000111222", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void editingABankKeepsItsOwnNameAndAccountWithoutTrippingTheDuplicateCheck() {
        Bank existing = bank("Awash Bank", "0132000111222", true);
        when(bankRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bankRepository.existsByNameIgnoreCaseAndAccountNumberAndIdNot("Awash Bank", "0132000111222", existing.getId()))
                .thenReturn(false);

        var response = service.update(existing.getId(), new BankRequest("Awash Bank", "New holder", "0132000111222", "main"));

        assertThat(response.accountName()).isEqualTo("New holder");
        assertThat(response.notes()).isEqualTo("main");
    }

    @Test
    void editingABankIntoAnotherRegisteredAccountIsRejected() {
        Bank existing = bank("Awash Bank", "0132000111222", true);
        when(bankRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bankRepository.existsByNameIgnoreCaseAndAccountNumberAndIdNot("Dashen Bank", "999", existing.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(existing.getId(), new BankRequest("Dashen Bank", null, "999", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void aBankCanBeDeactivatedAndReactivated() {
        Bank existing = bank("Awash Bank", "0132000111222", true);
        when(bankRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThat(service.setActive(existing.getId(), false).active()).isFalse();
        assertThat(service.setActive(existing.getId(), true).active()).isTrue();
    }

    @Test
    void theDropdownListShowsOnlyTheLastFourDigitsOfTheAccount() {
        when(bankRepository.findByActiveTrueOrderByNameAscAccountNumberAsc())
                .thenReturn(List.of(bank("Awash Bank", "0132000111222", true), bank("Tiny", "123", true)));

        var options = service.options();

        assertThat(options).hasSize(2);
        assertThat(options.get(0).name()).isEqualTo("Awash Bank");
        assertThat(options.get(0).accountEnding()).isEqualTo("1222");
        assertThat(options.get(1).accountEnding()).isEmpty();
    }
}

package com.company.erp.bank.dto;

import com.company.erp.bank.Bank;

import java.util.UUID;

/**
 * What a dropdown needs and nothing more: the bank's name and the last four digits of the account,
 * enough to tell two accounts at the same bank apart. The full account number is never in here, so
 * a cashier can pick a bank without being able to read the account details.
 */
public record BankOptionResponse(UUID id, String name, String accountEnding) {

    public static BankOptionResponse from(Bank bank) {
        String number = bank.getAccountNumber();
        String ending = number != null && number.length() > 4 ? number.substring(number.length() - 4) : "";
        return new BankOptionResponse(bank.getId(), bank.getName(), ending);
    }
}

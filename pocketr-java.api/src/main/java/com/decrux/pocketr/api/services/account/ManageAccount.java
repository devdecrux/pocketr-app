package com.decrux.pocketr.api.services.account;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateAccountDto;
import com.decrux.pocketr.api.entities.dtos.UpdateAccountDto;
import java.util.List;
import java.util.UUID;

public interface ManageAccount {

    AccountDto createAccount(CreateAccountDto dto, User owner);

    List<AccountDto> listIndividualAccounts(User owner);

    List<AccountDto> listAccountsByMode(User user, String mode, UUID householdId);

    AccountDto updateAccount(UUID id, UpdateAccountDto dto, User owner);
}

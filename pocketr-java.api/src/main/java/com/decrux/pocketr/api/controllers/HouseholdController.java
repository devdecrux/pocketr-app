package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateHouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdAccountShareDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdMemberDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdSummaryDto;
import com.decrux.pocketr.api.entities.dtos.InviteMemberDto;
import com.decrux.pocketr.api.entities.dtos.ShareAccountDto;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/households")
public class HouseholdController {

    private final ManageHousehold manageHousehold;

    public HouseholdController(ManageHousehold manageHousehold) {
        this.manageHousehold = manageHousehold;
    }

    @GetMapping
    public List<HouseholdSummaryDto> listHouseholds(@AuthenticationPrincipal User user) {
        return manageHousehold.listHouseholds(user);
    }

    @GetMapping("/{id}")
    public HouseholdDto getHousehold(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.getHousehold(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDto createHousehold(
            @RequestBody CreateHouseholdDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.createHousehold(dto, user);
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdMemberDto inviteMember(
            @PathVariable UUID id,
            @RequestBody InviteMemberDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.inviteMember(id, dto, user);
    }

    @PostMapping("/{id}/accept-invite")
    public HouseholdMemberDto acceptInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.acceptInvite(id, user);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveHousehold(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        manageHousehold.leaveHousehold(id, user);
    }

    @GetMapping("/{id}/shares")
    public List<HouseholdAccountShareDto> listSharedAccounts(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.listSharedAccounts(id, user);
    }

    @GetMapping("/{id}/accounts")
    public List<AccountDto> listHouseholdAccounts(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.listHouseholdAccounts(id, user);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdAccountShareDto shareAccount(
            @PathVariable UUID id,
            @RequestBody ShareAccountDto dto,
            @AuthenticationPrincipal User user
    ) {
        return manageHousehold.shareAccount(id, dto, user);
    }

    @DeleteMapping("/{id}/shares/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareAccount(
            @PathVariable UUID id,
            @PathVariable UUID accountId,
            @AuthenticationPrincipal User user
    ) {
        manageHousehold.unshareAccount(id, accountId, user);
    }
}

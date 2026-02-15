package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.*
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/households")
class HouseholdController(
    private val manageHousehold: ManageHousehold,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHousehold(
        @RequestBody dto: CreateHouseholdDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdDto {
        return manageHousehold.createHousehold(dto, user)
    }

    @GetMapping
    fun listHouseholds(@AuthenticationPrincipal user: User): List<HouseholdSummaryDto> {
        return manageHousehold.listHouseholds(user)
    }

    @GetMapping("/{id}")
    fun getHousehold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): HouseholdDto {
        return manageHousehold.getHousehold(id, user)
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    fun inviteMember(
        @PathVariable id: UUID,
        @RequestBody dto: InviteMemberDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdMemberDto {
        return manageHousehold.inviteMember(id, dto, user)
    }

    @PostMapping("/{id}/accept-invite")
    fun acceptInvite(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): HouseholdMemberDto {
        return manageHousehold.acceptInvite(id, user)
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveHousehold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageHousehold.leaveHousehold(id, user)
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    fun shareAccount(
        @PathVariable id: UUID,
        @RequestBody dto: ShareAccountDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdAccountShareDto {
        return manageHousehold.shareAccount(id, dto, user)
    }

    @DeleteMapping("/{id}/shares/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshareAccount(
        @PathVariable id: UUID,
        @PathVariable accountId: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageHousehold.unshareAccount(id, accountId, user)
    }

    @GetMapping("/{id}/shares")
    fun listSharedAccounts(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): List<HouseholdAccountShareDto> {
        return manageHousehold.listSharedAccounts(id, user)
    }

    @GetMapping("/{id}/accounts")
    fun listHouseholdAccounts(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): List<AccountDto> {
        return manageHousehold.listHouseholdAccounts(id, user)
    }
}

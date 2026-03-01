package com.decrux.pocketr.api.controllers

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.dtos.CategoryDto
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto
import com.decrux.pocketr.api.services.category.ManageCategory
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/categories")
class CategoryController(
    private val manageCategory: ManageCategory,
) {
    @GetMapping
    fun listCategories(
        @AuthenticationPrincipal user: User,
    ): List<CategoryDto> = manageCategory.listCategories(user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @RequestBody dto: CreateCategoryDto,
        @AuthenticationPrincipal user: User,
    ): CategoryDto = manageCategory.createCategory(dto, user)

    @PatchMapping("/{id}")
    fun updateCategory(
        @PathVariable id: UUID,
        @RequestBody dto: UpdateCategoryDto,
        @AuthenticationPrincipal user: User,
    ): CategoryDto = manageCategory.updateCategory(id, dto, user)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageCategory.deleteCategory(id, user)
    }
}

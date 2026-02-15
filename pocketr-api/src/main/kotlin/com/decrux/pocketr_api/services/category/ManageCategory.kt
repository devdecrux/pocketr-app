package com.decrux.pocketr_api.services.category

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.CategoryDto
import com.decrux.pocketr_api.entities.dtos.CreateCategoryDto
import com.decrux.pocketr_api.entities.dtos.UpdateCategoryDto
import java.util.UUID

interface ManageCategory {

    fun createCategory(dto: CreateCategoryDto, owner: User): CategoryDto

    fun listCategories(owner: User): List<CategoryDto>

    fun updateCategory(id: UUID, dto: UpdateCategoryDto, owner: User): CategoryDto

    fun deleteCategory(id: UUID, owner: User)
}

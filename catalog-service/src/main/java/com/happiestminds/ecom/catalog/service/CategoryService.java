package com.happiestminds.ecom.catalog.service;

import com.happiestminds.ecom.catalog.api.dto.CategoryRequest;
import com.happiestminds.ecom.catalog.api.dto.CategoryResponse;
import com.happiestminds.ecom.catalog.domain.Category;
import com.happiestminds.ecom.catalog.repository.CategoryRepository;
import com.happiestminds.ecom.catalog.service.exception.ConflictException;
import com.happiestminds.ecom.catalog.service.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByName(name)) {
            throw exists();
        }
        Category parent = null;
        if (request.parentId() != null && !request.parentId().isBlank()) {
            parent = categories.findById(request.parentId())
                    .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Parent category not found"));
        }
        try {
            return toResponse(categories.saveAndFlush(new Category(name, request.description(), parent)));
        } catch (DataIntegrityViolationException e) {
            throw exists();
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll().stream().map(CategoryService::toResponse).toList();
    }

    private static ConflictException exists() {
        return new ConflictException("CATEGORY_ALREADY_EXISTS", "A category with this name already exists");
    }

    private static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(),
                c.getParent() == null ? null : c.getParent().getId());
    }
}

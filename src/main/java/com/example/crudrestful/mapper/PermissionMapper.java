package com.example.crudrestful.mapper;

import org.mapstruct.Mapper;

import com.example.crudrestful.dto.request.PermissionRequest;
import com.example.crudrestful.dto.response.PermissionResponse;
import com.example.crudrestful.entity.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}

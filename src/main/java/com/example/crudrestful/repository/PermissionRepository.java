package com.example.crudrestful.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.crudrestful.entity.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {}

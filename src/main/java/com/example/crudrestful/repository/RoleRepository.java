package com.example.crudrestful.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.crudrestful.entity.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {}

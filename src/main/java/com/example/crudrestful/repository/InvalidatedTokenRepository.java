package com.example.crudrestful.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.crudrestful.entity.InvalidatedToken;

public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {}

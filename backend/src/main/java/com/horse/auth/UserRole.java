package com.horse.auth;

public enum UserRole {
	MEMBER,
	ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}
}

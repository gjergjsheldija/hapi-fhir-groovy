package com.gjergjsheldija.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppRouteTest {

	private static AppRoute route(String method, String path) {
		return new AppRoute(method, AppRoute.parsePath(path), "app", "op", false, "http://x", "s3cr3t");
	}

	@Test
	void parsesLiteralAndParamSegments() {
		assertEquals(List.of("Patient", "$daily-report", "{date}"), AppRoute.parsePath("Patient/$daily-report/{date}"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "/Patient/$x", "Patient//$x", "Patient/$x/", "a/{}", "a/{b", "a/b}", "a/x{b}", "a/{b c}"})
	void rejectsMalformedPaths(String path) {
		assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath(path));
	}

	@Test
	void rejectsNullPath() {
		assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath(null));
	}

	@Test
	void rejectsDuplicateParamNames() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath("$x/{a}/{a}"));
		assertTrue(e.getMessage().contains("duplicate"), e.getMessage());
	}

	@Test
	void matchesLiteralPath() {
		assertEquals(Optional.of(Map.of()), route("POST", "User/$register").match(List.of("User", "$register")));
	}

	@Test
	void matchExtractsRouteParams() {
		Optional<Map<String, String>> params = route("GET", "Patient/$daily-report/{date}")
			.match(List.of("Patient", "$daily-report", "2024-01-01"));
		assertEquals(Optional.of(Map.of("date", "2024-01-01")), params);
	}

	@Test
	void doesNotMatchDifferentLiteralOrLength() {
		AppRoute r = route("GET", "Patient/$daily-report/{date}");
		assertTrue(r.match(List.of("Patient", "$other", "2024-01-01")).isEmpty());
		assertTrue(r.match(List.of("Patient", "$daily-report")).isEmpty());
		assertTrue(r.match(List.of("Patient", "$daily-report", "2024-01-01", "extra")).isEmpty());
	}

	@Test
	void splitsRequestPath() {
		assertEquals(List.of("Patient", "$x", "1"), AppRoute.splitRequestPath("/Patient/$x/1"));
		assertEquals(List.of(), AppRoute.splitRequestPath(null));
		assertEquals(List.of(), AppRoute.splitRequestPath(""));
		assertEquals(List.of(), AppRoute.splitRequestPath("/"));
		// trailing slash yields an empty last segment, which never matches a parsed path
		assertEquals(List.of("a", ""), AppRoute.splitRequestPath("/a/"));
	}

	@Test
	void literalCountCountsNonParams() {
		assertEquals(2, route("GET", "Patient/$daily-report/{date}").literalCount());
	}

	@Test
	void collisionRequiresSameMethodAndShape() {
		AppRoute a = route("GET", "Patient/$r/{date}");
		assertTrue(a.collidesWith(route("GET", "Patient/$r/{day}")));
		assertFalse(a.collidesWith(route("POST", "Patient/$r/{date}")));
		assertFalse(a.collidesWith(route("GET", "Patient/$r/today")));
		assertFalse(a.collidesWith(route("GET", "Patient/$s/{date}")));
		assertFalse(a.collidesWith(route("GET", "Patient/$r")));
	}

	@Test
	void toStringDoesNotLeakSecret() {
		assertFalse(route("GET", "$x").toString().contains("s3cr3t"));
	}
}

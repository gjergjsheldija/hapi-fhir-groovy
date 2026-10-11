package com.gjergjsheldija.app;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One App operation compiled for routing. Segments are literals or {@code {name}} params.
 */
public record AppRoute(String method, List<String> segments, String appId, String operationId, boolean isPublic,
							  String endpointUrl, String secret) {

	private static final Pattern PARAM = Pattern.compile("\\{[A-Za-z0-9_-]+}");

	public AppRoute {
		segments = List.copyOf(segments);
	}

	public static List<String> parsePath(String path) {
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is empty");
		}
		if (path.startsWith("/")) {
			throw new IllegalArgumentException("path must not start with '/': " + path);
		}
		List<String> segments = List.of(path.split("/", -1));
		Set<String> paramNames = new HashSet<>();
		for (String segment : segments) {
			if (segment.isEmpty()) {
				throw new IllegalArgumentException("path has an empty segment: " + path);
			}
			if (isParam(segment)) {
				if (!paramNames.add(segment)) {
					throw new IllegalArgumentException("duplicate route param " + segment + " in " + path);
				}
			} else if (segment.contains("{") || segment.contains("}")) {
				throw new IllegalArgumentException("malformed route param '" + segment + "' in " + path);
			}
		}
		return segments;
	}

	public static boolean isParam(String segment) {
		return PARAM.matcher(segment).matches();
	}

	/**
	 * Expects the container-decoded path (HttpServletRequest#getPathInfo()); segments are not
	 * decoded again, so encoded characters cannot create extra segments or templates.
	 */
	public static List<String> splitRequestPath(String pathInfo) {
		if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
			return List.of();
		}
		String path = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
		return List.of(path.split("/", -1));
	}

	public long literalCount() {
		return segments.stream().filter(s -> !isParam(s)).count();
	}

	public Optional<Map<String, String>> match(List<String> requestSegments) {
		if (requestSegments.size() != segments.size()) {
			return Optional.empty();
		}
		Map<String, String> params = new LinkedHashMap<>();
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			String actual = requestSegments.get(i);
			if (isParam(segment)) {
				if (actual.isEmpty()) {
					return Optional.empty();
				}
				params.put(segment.substring(1, segment.length() - 1), actual);
			} else if (!segment.equals(actual)) {
				return Optional.empty();
			}
		}
		return Optional.of(params);
	}

	/**
	 * True when some request would match both routes with equal specificity, so neither can be
	 * preferred: same method, same length, every position compatible (equal literals, or a param
	 * on either side), and the same number of literal segments.
	 */
	public boolean collidesWith(AppRoute other) {
		if (!method.equals(other.method) || segments.size() != other.segments.size()
			|| literalCount() != other.literalCount()) {
			return false;
		}
		for (int i = 0; i < segments.size(); i++) {
			String a = segments.get(i);
			String b = other.segments.get(i);
			if (!isParam(a) && !isParam(b) && !a.equals(b)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return "AppRoute[" + method + " " + String.join("/", segments) + " -> " + appId + "#" + operationId
			+ (isPublic ? " public" : "") + "]";
	}
}

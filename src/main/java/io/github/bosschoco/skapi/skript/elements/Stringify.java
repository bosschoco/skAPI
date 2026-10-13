package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.registrations.Classes;

/** Skript 값을 문자열로. 정수인 실수(5.0)는 "5" 로 바꿔서 URL/헤더에 깔끔하게 들어가게 한다. */
final class Stringify {

	private Stringify() {
	}

	static String of(Object value) {
		if (value instanceof String text)
			return text;
		if (value instanceof Double || value instanceof Float) {
			double number = ((Number) value).doubleValue();
			if (Double.isFinite(number) && number == Math.rint(number) && Math.abs(number) < 9.007199254740992E15)
				return String.valueOf((long) number);
		}
		if (value instanceof Number number)
			return number.toString();
		return Classes.toString(value);
	}
}

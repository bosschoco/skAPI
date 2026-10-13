package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.conditions.base.PropertyCondition;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("API Response Is Successful")
@Description("네트워크 오류 없이 2xx(200~299) 상태 코드를 받았는지 확인합니다.")
@Example("""
	if {_res} is successful:
		send "성공!" to player
	else:
		send "실패: %api status of {_res}% %api error of {_res}%" to player
	""")
@Since("1.0.0")
public class CondApiSuccess extends PropertyCondition<ApiResponse> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION,
			PropertyCondition.infoBuilder(CondApiSuccess.class, PropertyType.BE, "success[ful]", "apiresponses")
				.supplier(CondApiSuccess::new)
				.build());
	}

	@Override
	public boolean check(ApiResponse response) {
		return response.isSuccess();
	}

	@Override
	protected String getPropertyName() {
		return "successful";
	}
}

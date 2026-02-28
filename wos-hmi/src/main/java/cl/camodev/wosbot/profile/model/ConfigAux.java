package cl.camodev.wosbot.profile.model;

public class ConfigAux {

	private String name;
	private String value;

	// Constructor vacío
	public ConfigAux() {
	}

	// Constructor con parámetros
	public ConfigAux(String name, String value) {
		this.name = name;
		this.value = value;
	}

	// Métodos para la propiedad 'name'
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	// Métodos para la propiedad 'value'
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "ConfigAux [name=" + name + ", value=" + value + "]";
	}

}

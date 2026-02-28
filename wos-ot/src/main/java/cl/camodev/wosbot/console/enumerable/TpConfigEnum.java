package cl.camodev.wosbot.console.enumerable;

public enum TpConfigEnum {
	//@formatter:off
	
	GLOBAL_CONFIG(1, "GLOBAL_CONFIG"), 
	PROFILE_CONFIG(2, "PROFILE_CONFIG"),;
	
	//@formatter:on

	private final int id;
	private final String name;

	TpConfigEnum(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}

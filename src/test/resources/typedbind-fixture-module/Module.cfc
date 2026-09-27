component extends="modules.BaseModule" {
    public string function showHelp() {
        out("fixture help");
        return "";
    }


	/**
	 * hint: Report which typed params were populated and with what values.
	 * @first first positional param (required)
	 * @second second positional param (optional)
	 */
	public string function report(
		required string first,
		string second = "default-second"
	) {
		out("first=" & arguments.first & " second=" & arguments.second);
		return "";
	}

	/**
	 * hint: Report with one required typed param only. Used to test missing-required.
	 * @only the only expected param
	 */
	public string function strict(required string only) {
		out("only=" & arguments.only);
		return "";
	}

}

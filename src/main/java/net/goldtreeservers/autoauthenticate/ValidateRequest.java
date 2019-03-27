package net.goldtreeservers.autoauthenticate;

public class ValidateRequest
{
	@SuppressWarnings("unused") private String clientToken;
	@SuppressWarnings("unused") private String accessToken;
	
	public ValidateRequest(String clientToken, String accessToken)
	{
		this.clientToken = clientToken;
		this.accessToken = accessToken;
	}
}

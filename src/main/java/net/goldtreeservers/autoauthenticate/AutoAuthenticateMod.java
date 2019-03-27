package net.goldtreeservers.autoauthenticate;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.Agent;
import com.mojang.authlib.UserAuthentication;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = "autoauthenticate", name = "Auto Authenticate", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8,1.8.9]")
public class AutoAuthenticateMod
{
	private static final JsonParser JSON_PARSER = new JsonParser();
	
	private static Method USER_AUTHENICATION_MAKE_REQUEST_METHOD = null;
	private static URL USER_AUTHENICATION_VALIDATE_URL = null;
	
	private static Field GUI_DISCONENCTED_REASON_FIELD = null;
	
	private static Field SESSION_TOKEN_FIELD = null;
	
	private static boolean loaded = false;
	
	static
	{
		try
		{
			AutoAuthenticateMod.USER_AUTHENICATION_MAKE_REQUEST_METHOD = YggdrasilAuthenticationService.class.getDeclaredMethod("makeRequest", URL.class, Object.class, Class.class);
			AutoAuthenticateMod.USER_AUTHENICATION_MAKE_REQUEST_METHOD.setAccessible(true);
			
			Field routeField = YggdrasilUserAuthentication.class.getDeclaredField("ROUTE_VALIDATE");
			routeField.setAccessible(true);
			
			AutoAuthenticateMod.USER_AUTHENICATION_VALIDATE_URL = (URL)routeField.get(null);
			
			for(Field field : GuiDisconnected.class.getDeclaredFields())
			{
				if (field.getType().equals(IChatComponent.class))
				{
					AutoAuthenticateMod.GUI_DISCONENCTED_REASON_FIELD = field;
					AutoAuthenticateMod.GUI_DISCONENCTED_REASON_FIELD.setAccessible(true);
					
					break;
				}
			}
			
			for(Field field : Session.class.getDeclaredFields())
			{
				if (field.getType().equals(String.class))
				{
					AutoAuthenticateMod.SESSION_TOKEN_FIELD = field;
				}
			}
			
			AutoAuthenticateMod.SESSION_TOKEN_FIELD.setAccessible(true);
			
			AutoAuthenticateMod.loaded = true;
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
	}
	
	@Mod.EventHandler
	public void onPreInit(FMLPreInitializationEvent event)
	{
		if (!AutoAuthenticateMod.loaded)
		{
			return;
		}
		
		MinecraftForge.EVENT_BUS.register(this);
	}
	
    @SubscribeEvent
    public void onInitGuiPostEvent(InitGuiEvent.Pre event)
    {
    	GuiScreen gui = event.gui;
    	if (gui instanceof GuiDisconnected)
    	{
    		final GuiDisconnected disconnectedGui = (GuiDisconnected)gui;

    		String reason = AutoAuthenticateMod.getReason(disconnectedGui);
    		if (reason.startsWith("Failed to login: Invalid session"))
    		{
    			AutoAuthenticateMod.updateReason(disconnectedGui, "\n\nAutoAuthenicate: We will try to reauthenicate you... ");

        		//Use schedule so we can render the the message
        		Minecraft.getMinecraft().addScheduledTask(new Runnable()
				{
					@Override
					public void run()
					{
		        		try
		        		{
							File launcherProfilesFile = new File("launcher_profiles.json");
							if (!launcherProfilesFile.exists())
							{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Launcher profiles was not found");

								return;
							}
							
							JsonObject launcherProfiles;
							try
							{
								launcherProfiles = AutoAuthenticateMod.JSON_PARSER.parse(new String(Files.readAllBytes(launcherProfilesFile.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
							}
							catch(Throwable e)
							{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Malformed launcher profiles?");

								return;
							}
							
							JsonElement clientToken = launcherProfiles.get("clientToken");
							if (clientToken == null)
							{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Client token missing");

								return;
							}
							
			        		YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Minecraft.getMinecraft().getProxy(), clientToken.getAsString());

			        		try
			        		{
			        			//The response is empty one
			        			
			        			ValidateResponse validateResponse = (ValidateResponse)AutoAuthenticateMod.USER_AUTHENICATION_MAKE_REQUEST_METHOD.invoke(authService, AutoAuthenticateMod.USER_AUTHENICATION_VALIDATE_URL, new ValidateRequest(authService.getClientToken(), Minecraft.getMinecraft().getSession().getToken()), ValidateResponse.class);
			        			if (validateResponse != null)
			        			{
					    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Log in to the launcher");

									return;
			        			}
			        		}
			        		catch(Throwable e)
			        		{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Log in to the launcher");

								return;
			        		}
			        		
			        		UserAuthentication userAuth = authService.createUserAuthentication(Agent.MINECRAFT);
			        		
			        		Map<String, Object> properties = new HashMap<String, Object>();
			        		properties.put("username", Minecraft.getMinecraft().getSession().getUsername());
			        		properties.put("userid", Minecraft.getMinecraft().getSession().getPlayerID());
			        		properties.put("accessToken", Minecraft.getMinecraft().getSession().getToken());
			        		
			        		userAuth.loadFromStorage(properties);
			        		
			        		try
			        		{
			        			userAuth.logIn();
			        		}
			        		catch(Throwable e)
			        		{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Relaunch your game, we lost round two");

								return;
			        		}
			        		
			        		AutoAuthenticateMod.setFinal(Minecraft.getMinecraft().getSession(), AutoAuthenticateMod.SESSION_TOKEN_FIELD, userAuth.getAuthenticatedToken());

		        			boolean save = false;
		        			
			        		//Last step is to save it so the launcher won't broke
			        		JsonObject authDb = launcherProfiles.getAsJsonObject("authenticationDatabase");
			        		if (authDb != null)
			        		{
			        			for(Entry<String, JsonElement> entries : authDb.entrySet())
			        			{
			        				JsonObject entry = entries.getValue().getAsJsonObject();
			        				if (entry != null)
			        				{
				        				JsonElement accessToken = entry.get("accessToken");
				        				if (accessToken != null && accessToken.getAsString().equals(properties.get("accessToken"))) //Compare to old access token to replace with new
				        				{
				        					entry.remove("accessToken");
		        							entry.addProperty("accessToken", userAuth.getAuthenticatedToken());
		        							
		        							save = true;
				        				}
			        				}
			        			}
			        		}
		        			
		        			if (save)
		        			{
				        		try
				        		{
					        		Files.write(launcherProfilesFile.toPath(), launcherProfiles.toString().getBytes(StandardCharsets.UTF_8));
				        		}
				        		catch(Throwable e)
				        		{
					    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Failed to save new launch profiles to the disk");
					    			
					    			return;
				        		}
		        			}
		        			else
		        			{
				    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: We were unable to save the new access token, you need to relogin to the launcher next time");
		        			}
		        			
			    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Success! Try to relog");
		        		}
		        		catch(Throwable e)
		        		{
			    			AutoAuthenticateMod.updateReason(disconnectedGui, "\nAutoAuthenicate: Its all downhill from here");
			    			
		        			e.printStackTrace();
		        		}
					}
				});
    		}
    	}
    }
    
    private static String getReason(GuiDisconnected gui)
    {
    	try
    	{
    		IChatComponent reason = (IChatComponent)AutoAuthenticateMod.GUI_DISCONENCTED_REASON_FIELD.get(gui);
    		
    		return reason.getUnformattedText().trim();
		}
    	catch (Throwable e)
    	{
    	}
    	
		return "";
    }
    
    private static void updateReason(GuiDisconnected gui, String message)
    {
		try
		{
			IChatComponent reason = (IChatComponent)AutoAuthenticateMod.GUI_DISCONENCTED_REASON_FIELD.get(gui);
			
			AutoAuthenticateMod.GUI_DISCONENCTED_REASON_FIELD.set(gui, reason.appendText(message));
			
			gui.initGui(); //Redraw
		}
		catch (Throwable e)
		{
		}
    }
    
    private static void setFinal(Object target, Field field, Object newValue) throws Exception
    {
    	field.setAccessible(true);

    	Field modifiersField = Field.class.getDeclaredField("modifiers");
    	modifiersField.setAccessible(true);
    	modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    	field.set(target, newValue);
	}
}

package com.developers.uberanimation;

import com.developers.uberanimation.Remote.IGoogleApi;
import com.developers.uberanimation.Remote.RetrofitClient;

/**
 * Created by ANTHONY on 22/01/2018.
 */

public class Common {
    public static final String baseURL = "https://googleapis.com";
    public static IGoogleApi getGoogleAPI(){
        return RetrofitClient.getClient(baseURL).create(IGoogleApi.class);
    }
}

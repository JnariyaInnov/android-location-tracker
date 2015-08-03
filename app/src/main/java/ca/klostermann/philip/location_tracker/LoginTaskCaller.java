package ca.klostermann.philip.location_tracker;

import com.firebase.client.AuthData;
import com.firebase.client.FirebaseError;

public interface LoginTaskCaller {
    public abstract void onLoginSuccess(AuthData authData);
    public abstract void onLoginFailure(FirebaseError firebaseError);
}

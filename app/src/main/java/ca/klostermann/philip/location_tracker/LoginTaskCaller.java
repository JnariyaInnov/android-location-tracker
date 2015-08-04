package ca.klostermann.philip.location_tracker;

import com.firebase.client.AuthData;
import com.firebase.client.FirebaseError;

public interface LoginTaskCaller {
    void onLoginSuccess(AuthData authData);
    void onLoginFailure(FirebaseError firebaseError);
}

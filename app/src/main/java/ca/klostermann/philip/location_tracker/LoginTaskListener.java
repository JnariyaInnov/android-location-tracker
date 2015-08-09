package ca.klostermann.philip.location_tracker;

import com.firebase.client.AuthData;
import com.firebase.client.FirebaseError;

public interface LoginTaskListener {
    void onLoginSuccess(AuthData authData);
    void onLoginFailure(FirebaseError firebaseError);
}

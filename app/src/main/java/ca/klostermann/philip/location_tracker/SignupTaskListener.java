package ca.klostermann.philip.location_tracker;

import com.firebase.client.FirebaseError;
import java.util.Map;

public interface SignupTaskListener {
    void onSignupSuccess(Map<String, Object> result);
    void onSignupFailure(FirebaseError firebaseError);
}

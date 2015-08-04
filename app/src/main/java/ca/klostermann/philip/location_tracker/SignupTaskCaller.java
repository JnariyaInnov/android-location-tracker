package ca.klostermann.philip.location_tracker;

import com.firebase.client.FirebaseError;
import java.util.Map;

public interface SignupTaskCaller {
    void onSignupSuccess(Map<String, Object> result);
    void onSignupFailure(FirebaseError firebaseError);
}

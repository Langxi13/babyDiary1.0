package io.github.langxi13.babydiary;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeShareReceiverPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

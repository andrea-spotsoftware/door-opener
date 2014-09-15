package it.spot.io.dooropener;



import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.Locale;


public class Beam extends Activity implements OnNdefPushCompleteCallback, CreateNdefMessageCallback { //

    IntentFilter[] intentFiltersArray;
    PendingIntent mPendingIntent;

    NfcAdapter mNfcAdapter;
    TextView mInfoText;
    private static final int MESSAGE_SENT = 1;
    private String mKey = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beam);

        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter ndefIntentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefIntentFilter.addDataType("application/it.spot.io.dooropener");
        }
        catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }
        intentFiltersArray = new IntentFilter[] {ndefIntentFilter};

        mInfoText = (TextView) findViewById(R.id.textView);
        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            mInfoText = (TextView) findViewById(R.id.textView);
            mInfoText.setText("NFC is not available on this device.");
        }


//        //Received msg:
//        mNfcAdapter.setNdefPushMessageCallback(this, this, this);
//      mNfcAdapter.setNdefPushMessage(createNdefMessage("second message(credentials or oauth token)"), this);
//        // Register callback to listen for message-sent success
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }


    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    public NdefMessage createNdefMessage(String text) {
        NdefMessage msg = new NdefMessage(
                new NdefRecord[] {
                        //createMimeRecord("application/it.spot.io.dooropener", text.getBytes()),
                        createTextRecord(text, Locale.ENGLISH, true)
                });
        return msg;
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        Log.i("TAG", "push completed");
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, intentFiltersArray, null);

    }

    /** This handler receives a message from onNdefPushComplete */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SENT:
                    Toast.makeText(getApplicationContext(), "Message sent!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("TAG", "on pause");
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("TAG", "on resume");
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, intentFiltersArray, null);
    }

    @Override
    public void onNewIntent(Intent intent) {

        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Log.i("TAG", "on new intent");

            processNdefMessage(intent);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mNfcAdapter.setNdefPushMessage(createNdefMessage("second message(credentials or oauth token)"), Beam.this);
                }
            }, 200);

        }
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processNdefMessage(Intent intent) {
        final Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present
        //mKey = new String(msg.getRecords()[0].getPayload());
        mInfoText.setText(new String(msg.getRecords()[0].getPayload()));

//        Intent newIntent = new Intent(Beam.this, CredentialsPushNFC.class);
//        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        newIntent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, rawMsgs);
//        startActivity(newIntent);

//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mNfcAdapter.disableForegroundDispatch(Beam.this);
//
//                Intent newIntent = new Intent(Beam.this, CredentialsPushNFC.class);
//                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                newIntent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, rawMsgs);
//                startActivity(newIntent);
//            }
//        });
    }

    /**
     * Creates a custom MIME type encapsulated in an NDEF record
     *
     * @param mimeType
     */
    public NdefRecord createMimeRecord(String mimeType, byte[] payload) {
        byte[] mimeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
        NdefRecord mimeRecord = new NdefRecord(
                NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
        return mimeRecord;
    }

    public NdefRecord createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = payload.getBytes(utfEncoding);
        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT, new byte[0], data);
        return record;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If NFC is not available, we won't be needing this menu
        if (mNfcAdapter == null) {
            return super.onCreateOptionsMenu(menu);
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.beam, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(Settings.ACTION_NFCSHARING_SETTINGS);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        return createNdefMessage("second message(credentials or oauth token 121 1)");
    }
}
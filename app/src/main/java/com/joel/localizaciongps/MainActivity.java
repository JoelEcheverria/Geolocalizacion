package com.joel.localizaciongps;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;

import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks,
        LocationListener {

    private static final String LOGTAG = "android-localizacion";

    private static final int PETICION_PERMISO_LOCALIZACION = 101;
    private static final int PETICION_CONFIG_UBICACION = 201;

    private GoogleApiClient apiClient;

    private Button btnCapturar;
    private TextView tvLongitud;
    private TextView tvLatitud;
    private ToggleButton btnActualizar;

    private LocationRequest locRequest;
    private ListView lista;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lista = (ListView)findViewById(R.id.lista);
        btnCapturar= (Button)findViewById(R.id.btnCapturar);
        tvLatitud= (TextView)findViewById(R.id.tvLatitud);
        tvLongitud= (TextView) findViewById(R.id.tvLongitud);
        btnActualizar = (ToggleButton) findViewById(R.id.btnActualizar);

        btnActualizar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleLocationUpdates(btnActualizar.isChecked());
            }
        });

        //Construcción cliente API Google
        apiClient = new GoogleApiClient.Builder(this)//dar acceso a los servicios google
                .enableAutoManage(this, this)//conecta y desconecta automaticamente a los servicios google
                .addConnectionCallbacks(this)//observa las conexiones y desconexiones
                .addApi(LocationServices.API)//indica la api de los servicios que vamos a acceder
                .build();//inicia la conexion de los servicios solicitados
    }

    private void toggleLocationUpdates(boolean enable) {
        if (enable) {
            enableLocationUpdates();
            btnActualizar.setText("LOCATION ON");
        } else {
            disableLocationUpdates();
            btnActualizar.setText("LOCATION OFF");
        }
    }

    private void enableLocationUpdates() {

        locRequest = new LocationRequest();
        locRequest.setInterval(3000);
        locRequest.setFastestInterval(1000);
        locRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest locSettingsRequest =//compara los requisitos de la aplicacion
                new LocationSettingsRequest.Builder()
                        .addLocationRequest(locRequest)
                        .build();

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        apiClient, locSettingsRequest);

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();//obtiene el resultado de comparacion
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS://la configuracion del dispositivo es valida

                        Log.i(LOGTAG, "Configuración correcta");
                        startLocationUpdates();

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED://Indica que la configuración actual del dispositivo no es suficiente para nuestra aplicación

                        try {
                            Log.i(LOGTAG, "Se requiere actuación del usuario");
                            status.startResolutionForResult(MainActivity.this, PETICION_CONFIG_UBICACION);
                        } catch (IntentSender.SendIntentException e) {
                            btnActualizar.setChecked(false);
                            Log.i(LOGTAG, "Error al intentar solucionar configuración de ubicación");
                        }

                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE://no existe ninguna accion del usuario y no es suficiente
                        Log.i(LOGTAG, "No se puede cumplir la configuración de ubicación necesaria");
                        btnActualizar.setChecked(false);
                        break;
                }
            }
        });
    }

    private void disableLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);//detener la actualizacion

    }

    private void startLocationUpdates() {//solicitar el inicio de actualizaciones
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            //Ojo: estamos suponiendo que ya tenemos concedido el permiso.
            //Sería recomendable implementar la posible petición en caso de no tenerlo.

            Log.i(LOGTAG, "Inicio de recepción de ubicaciones");
            //envia al metodo onLocationChanged los requerimientos actualizados
            LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locRequest, MainActivity.this);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {// se activara en caso de un error
        //Se ha producido un error que no se puede resolver automáticamente
        //y la conexión con los Google Play Services no se ha establecido.

        Log.e(LOGTAG, "Error grave al conectar con Google Play Services");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {//se ejecutara cuando se realice la conexion
        //Conectado correctamente a Google Play Services
                            //verifica si la aplicacion tiene concedido dichos permisos
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {//si no esta solicitado los permisos

            ActivityCompat.requestPermissions(this,// en caso de no tener los permisos llamar a este metodo
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PETICION_PERMISO_LOCALIZACION);
        } else {

            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);//conseguimos la ultima posicion geografica

            updateUI(lastLocation);//muestra los datos de actividad principal
        }
    }

    @Override
    public void onConnectionSuspended(int i) {//se activara cuando la conexion se pierda temporalmente
        //Se ha interrumpido la conexión con Google Play Services

        Log.e(LOGTAG, "Se ha interrumpido la conexión con Google Play Services");
    }


    int i=0;
    int j=0;
    String memoria[]= new String[10];

    List<String> listPerson = new ArrayList<String>();
    private void updateUI(Location loc) {//muestra los datos de actividad principal
        if (loc != null) {
            String lc=tvLatitud.getText().toString()+"  "+tvLongitud.getText().toString();
            tvLatitud.setText(String.valueOf("Latitud: " + loc.getLatitude()));
            tvLongitud.setText(String.valueOf("Longitud: " + loc.getLongitude()));

            ArrayList<String> list = new ArrayList<String>();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item,list);
            lista.setAdapter(adapter);

            btnCapturar.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {

                    if (btnActualizar.isChecked()&&1>i){
                        btnActualizar.setChecked(false);
                        disableLocationUpdates();
                        list.add(0,lc);
                        adapter.notifyDataSetChanged();
                        memoria[j]=lista.getAdapter().getItem(0).toString();
                        i++;
                        j++;

                    }

                    if (btnActualizar.isChecked()&&2>i){
                        btnActualizar.setChecked(false);
                        disableLocationUpdates();
                        list.add(0,memoria[j-1]);
                        list.add(1,lc);
                        adapter.notifyDataSetChanged();
                        memoria[j]=lista.getAdapter().getItem(1).toString();
                        i++;//1 2
                        j++;
                    }

                    if (btnActualizar.isChecked()&&3>i){
                        btnActualizar.setChecked(false);
                        disableLocationUpdates();
                        list.add(0,memoria[j-2]);
                        list.add(1,memoria[j-1]);
                        list.add(2,lc);
                        memoria[j]=lista.getAdapter().getItem(2).toString();
                        adapter.notifyDataSetChanged();
                        i++;
                        j++;
                    }
                    if (btnActualizar.isChecked()&&4>i){
                        btnActualizar.setChecked(false);
                        disableLocationUpdates();
                        list.add(0,memoria[j-3]);
                        list.add(1,memoria[j-2]);
                        list.add(2,memoria[j-1]);
                        list.add(3,lc);
                        memoria[j]=lista.getAdapter().getItem(3).toString();
                        adapter.notifyDataSetChanged();
                        i++;
                        j++;
                    }
                    if (btnActualizar.isChecked()&&5>i){
                        btnActualizar.setChecked(false);
                        disableLocationUpdates();
                        list.add(0,memoria[j-4]);
                        list.add(1,memoria[j-3]);
                        list.add(2,memoria[j-2]);
                        list.add(3,memoria[j-1]);
                        list.add(4,lc);
                        adapter.notifyDataSetChanged();
                        i++;
                        j++;
                    }
            }

            });

        }
    }





    @Override//para conocer el resultado de la peticion
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PETICION_PERMISO_LOCALIZACION) {
            if (grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                //Permiso concedido

                @SuppressWarnings("MissingPermission")
                Location lastLocation =
                        LocationServices.FusedLocationApi.getLastLocation(apiClient);

                updateUI(lastLocation);

            } else {
                //Permiso denegado:
                //Deberíamos deshabilitar toda la funcionalidad relativa a la localización.

                Log.e(LOGTAG, "Permiso denegado");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PETICION_CONFIG_UBICACION:
                switch (resultCode) {
                    case Activity.RESULT_OK://indica que el usuario a realizado el cambio solicitado/ se puede solicitar el inicio de las actualizaciones
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:// indica que el usuario no ha recibido ningun cambio/ desactiva el boton de inicio de las actualizaciones
                        Log.i(LOGTAG, "El usuario no ha realizado los cambios de configuración necesarios");
                        btnActualizar.setChecked(false);
                        break;
                }
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {//recibe los datos actualizados

        Log.i(LOGTAG, "Recibida nueva ubicación!");

        //Mostramos la nueva ubicación recibida
        updateUI(location);
    }
}


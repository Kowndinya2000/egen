double total_distance = 0;
protected static final String TAG = "location-updates-sample";
private static  long UPDATE_INTERVAL_IN_MILLISECONDS = 13000;

public boolean global_button = false;
private final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
private final static String LOCATION_KEY = "location-key";
private final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

public static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
private static final int REQUEST_CHECK_SETTINGS = 10;
public GoogleApiClient mGoogleApiClient;
private LocationRequest mLocationRequest;
private Location mCurrentLocation;
private String mLastUpdateTime;
private String percentageLabel,timeRunning;

protected class Interval
{
    protected long MIN_INTERVAL;
    protected long STEP;
    protected int POLICY_NUMBER;
    protected FunctionWrap global_wrap;
    protected final FunctionWrap LINEAR = () ->   MIN_INTERVAL+(STEP*(100-Long.parseLong(getCurrentBatteryLevel())));
    protected boolean runInBackground;
    protected long MAGNITUDE = 0;
    protected boolean inForeGround = true;
    protected boolean isCharging;
    protected ArrayList<Integer> Policy_array = new ArrayList<>();
    protected ArrayList<String> AppState_array = new ArrayList<>();
    protected ArrayList<Integer> StepCount_array = new ArrayList<>();
    protected ArrayList<Integer> SensingInterval_array = new ArrayList<>();
    protected ArrayList<String> BatteryLevel_array = new ArrayList<>();
    protected ArrayList<Integer> Threshold_High_array = new ArrayList<>();
    protected ArrayList<Integer> Threshold_Medium_array = new ArrayList<>();
    protected ArrayList<String> BatteryState_array = new ArrayList<>();

}


protected Interval setUpIntervalObject = new Interval();
protected void runAppInBackground(boolean flag,long Magnitude)
{
    if(Magnitude < 1) setUpIntervalObject.MAGNITUDE = 1;
    setUpIntervalObject.MAGNITUDE = Magnitude;
    setUpIntervalObject.runInBackground = flag;
}
protected String setUpLocationService()
{
    String[] arrOfPolicies = "30, 1".split(", ") ;
				        String[] arrOfStepCounts = "20, 30".split(", ") ;
				        String[] arrOfAppStates = "Foreground, Background".split(", ") ;
				        String[] arrOfSensingIntervals = "2500, 3000".split(", ") ;
				        String[] arrOfBatteryLevels = "High, Medium".split(", ") ;
				        String[] arrOfMediumThresholds = "46, 80".split(", ") ;
				        String[] arrOfHighThresholds = "63, 50".split(", ") ;
				        String[] arrOfBatteryStates = "Charging, Discharging".split(", ") ;
				    
				        for(int i=0;i<arrOfPolicies.length;i++)
				        {
				            setUpIntervalObject.Policy_array.add(Integer.parseInt(arrOfPolicies[i]));
				            setUpIntervalObject.StepCount_array.add(Integer.parseInt(arrOfStepCounts[i]));
				            setUpIntervalObject.AppState_array.add(arrOfAppStates[i]);
				            setUpIntervalObject.SensingInterval_array.add(Integer.parseInt(arrOfSensingIntervals[i]));
				            setUpIntervalObject.BatteryLevel_array.add(arrOfBatteryLevels[i]);
				            setUpIntervalObject.Threshold_Medium_array.add(Integer.parseInt(arrOfMediumThresholds[i]));
				            setUpIntervalObject.Threshold_High_array.add(Integer.parseInt(arrOfHighThresholds[i]));
				            setUpIntervalObject.BatteryState_array.add(arrOfBatteryStates[i]);
				        }
				        String BatteryState,AppState,BatteryLevel;
				    
				        if(setUpIntervalObject.isCharging)
				        {
				            BatteryState = "Charging";
				        }
				        else{
				            BatteryState = "Discharging";
				        }
				        if(setUpIntervalObject.inForeGround = true)
				        {
				            AppState = "Foreground";
				        }
				        else{
				            AppState = "Background";
				        }
				        for(int i=0;i<setUpIntervalObject.Policy_array.size();i++)
				        {
				           if(Integer.parseInt(percentageLabel) >= setUpIntervalObject.Threshold_High_array.get(i))
				            {
				                BatteryLevel = "High";
				            }
				            else if(Integer.parseInt(percentageLabel) >= setUpIntervalObject.Threshold_Medium_array.get(i))
				            {
				                BatteryLevel = "Medium";
				            }
				            else{
				                BatteryLevel = "Low";
				            }
				            if(BatteryLevel.equals(setUpIntervalObject.BatteryLevel_array.get(i)))
				            {
				                if(BatteryState.equals(setUpIntervalObject.BatteryState_array.get(i)))
				                {
				                    if(AppState.equals(setUpIntervalObject.AppState_array.get(i)))
				                    {
				                        setUpIntervalObject.global_wrap = setUpIntervalObject.LINEAR;
				                        setUpIntervalObject.MIN_INTERVAL = setUpIntervalObject.SensingInterval_array.get(i);
				                        setUpIntervalObject.STEP = setUpIntervalObject.StepCount_array.get(i);
				                        setUpIntervalObject.POLICY_NUMBER = setUpIntervalObject.Policy_array.get(i);
				                        runAppInBackground  (AppState.equals("Foreground"), setUpIntervalObject.STEP);
				                        return Long.toString(setUpIntervalObject.global_wrap.execute());
				                    }
				                }
				            }
				        }
				        return "13000";
				    }
				    interface FunctionWrap
				    {
				        long execute();
				    }
				    public void onBatteryLevelChanged()
				    {
				    
				    }
				    BroadcastReceiver BatteryInfoReceiver = new BroadcastReceiver(){
				        @Override
				        public void onReceive(Context ctxt, Intent intent) {
				            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
				            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
				            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
				            setUpIntervalObject.isCharging = isCharging;
				            String val = String.valueOf(level);
				            int per = Integer.parseInt(val);
				            long rem = 100 - per;
				            percentageLabel = String.valueOf(level);
				            timeRunning = setUpLocationService();
				            createLocationRequest();
				            Log.i("Running in Foreground: ", String.valueOf(UPDATE_INTERVAL_IN_MILLISECONDS));
				            onBatteryLevelChanged();
				            if (mGoogleApiClient.isConnected() && global_button) {
				                startLocationUpdates();
				            }
				        }
				    };

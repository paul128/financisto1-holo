/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package tw.tib.financisto.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import tw.tib.financisto.R;
import tw.tib.financisto.datetime.DateUtils;
import tw.tib.financisto.datetime.Period;
import tw.tib.financisto.datetime.PeriodType;
import tw.tib.financisto.blotter.BlotterFilter;
import tw.tib.financisto.filter.DateTimeCriteria;
import tw.tib.financisto.filter.WhereFilter;
import tw.tib.financisto.utils.MyPreferences;

import java.text.DateFormat;
import java.util.Calendar;

import static tw.tib.financisto.utils.EnumUtils.createSpinnerAdapter;

public class DateFilterActivity extends Activity {
	
	public static final String EXTRA_FILTER_PERIOD_TYPE = "filter_period_type";
	public static final String EXTRA_FILTER_PERIOD_FROM = "filter_period_from";
	public static final String EXTRA_FILTER_PERIOD_TO = "filter_period_to";
	public static final String EXTRA_FILTER_DONT_SHOW_NO_FILTER = "filter_dont_show_no_filter";
    public static final String EXTRA_FILTER_SHOW_PLANNER = "filter_show_planner";

	private final Calendar cFrom = Calendar.getInstance(); 
	private final Calendar cTo = Calendar.getInstance();
	
	private Spinner spinnerPeriodType;
	private Button buttonPeriodFrom;
	private Button buttonPeriodTo;
	
	private DateFormat df;
    private PeriodType[] periods = PeriodType.allRegular();

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MyPreferences.switchLocale(base));
	}

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.date_filter);

		df = DateUtils.getShortDateFormat(this);

        Intent intent = getIntent();
        setCorrectPeriods(intent);
        createPeriodsSpinner();

		buttonPeriodFrom = (Button)findViewById(R.id.bPeriodFrom);
		buttonPeriodFrom.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				showDialog(1);
			}
		});		
		buttonPeriodTo = (Button)findViewById(R.id.bPeriodTo);	
		buttonPeriodTo.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				showDialog(2);
			}
		});		
		
		Button bOk = (Button)findViewById(R.id.bOK);
		bOk.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent data = new Intent();
				PeriodType period = periods[spinnerPeriodType.getSelectedItemPosition()];
				data.putExtra(EXTRA_FILTER_PERIOD_TYPE, period.name());
				data.putExtra(EXTRA_FILTER_PERIOD_FROM, cFrom.getTimeInMillis());
				data.putExtra(EXTRA_FILTER_PERIOD_TO, cTo.getTimeInMillis());
				setResult(RESULT_OK, data);
				finish();
			}
		});
		
		Button bCancel = (Button)findViewById(R.id.bCancel);
		bCancel.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
		
		Button bNoFilter = (Button)findViewById(R.id.bNoFilter);
		bNoFilter.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setResult(RESULT_FIRST_USER);
				finish();
			}
		});		

		if (intent == null) {
			reset();
		} else {
			WhereFilter filter = WhereFilter.fromIntent(intent);
			DateTimeCriteria c = (DateTimeCriteria)filter.get(BlotterFilter.DATETIME);
			if (c != null) {
				if (c.getPeriod() == null || c.getPeriod().type == PeriodType.CUSTOM) {
					selectPeriod(c.getLongValue1(), c.getLongValue2());					
				} else {
					selectPeriod(c.getPeriod());
				}
				
			}
			if (intent.getBooleanExtra(EXTRA_FILTER_DONT_SHOW_NO_FILTER, false)) {
				bNoFilter.setVisibility(View.GONE);
			}
		}
	}

    private void setCorrectPeriods(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_FILTER_SHOW_PLANNER, false)) {
            periods = PeriodType.allPlanner();
        }
    }

    private void createPeriodsSpinner() {
        spinnerPeriodType = (Spinner) findViewById(R.id.period);
        spinnerPeriodType.setAdapter(createSpinnerAdapter(this, periods));
        spinnerPeriodType.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PeriodType period = periods[position];
                if (period == PeriodType.CUSTOM) {
                    selectCustom();
                } else {
                    selectPeriod(period);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    private void selectPeriod(Period p) {
		spinnerPeriodType.setSelection(indexOf(p.type));
	}

	private void selectPeriod(long from, long to) {
		cFrom.setTimeInMillis(from);
		cTo.setTimeInMillis(to);			
		spinnerPeriodType.setSelection(indexOf(PeriodType.CUSTOM));
	}

    private int indexOf(PeriodType type) {
        for (int i = 0; i < periods.length; i++) {
            if (periods[i] == type) {
                return i;
            }
        }
        return 0;
    }

    @Override
	protected Dialog onCreateDialog(final int id) {
		final Dialog d = new Dialog(this);
		d.setCancelable(true);
		d.setTitle(id == 1 ? R.string.period_from : R.string.period_to);
		d.setContentView(R.layout.filter_period_select);
		Button bOk = (Button)d.findViewById(R.id.bOK);
		bOk.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setDialogResult(d, id == 1 ? cFrom : cTo);
				d.dismiss();
			}
		});		
		Button bCancel = (Button)d.findViewById(R.id.bCancel);
		bCancel.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				d.cancel();
			}
		});
		return d;
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		prepareDialog(dialog, id == 1 ? cFrom : cTo);
	}

	private void prepareDialog(Dialog dialog, Calendar c) {
		DatePicker dp = (DatePicker)dialog.findViewById(R.id.date);
		dp.init(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), null);
		TimePicker tp = (TimePicker)dialog.findViewById(R.id.time);
        tp.setIs24HourView(DateUtils.is24HourFormat(this));
        tp.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
		tp.setCurrentMinute(c.get(Calendar.MINUTE));
	}

	private void setDialogResult(Dialog d, Calendar c) {
		DatePicker dp = (DatePicker)d.findViewById(R.id.date);
		c.set(Calendar.YEAR, dp.getYear());
		c.set(Calendar.MONTH, dp.getMonth());
		c.set(Calendar.DAY_OF_MONTH, dp.getDayOfMonth());
		TimePicker tp = (TimePicker)d.findViewById(R.id.time);
		c.set(Calendar.HOUR_OF_DAY, tp.getCurrentHour());
		c.set(Calendar.MINUTE, tp.getCurrentMinute());
		updateDate();
	}

	private void enableButtons() {
		buttonPeriodFrom.setEnabled(true);
		buttonPeriodTo.setEnabled(true);
	}

	private void disableButtons() {
		buttonPeriodFrom.setEnabled(false);
		buttonPeriodTo.setEnabled(false);
	}

	private void updateDate(Period p) {
		cFrom.setTimeInMillis(p.start);
		cTo.setTimeInMillis(p.end);
		updateDate();
	}
	
	private void updateDate() {
		buttonPeriodFrom.setText(df.format(cFrom.getTime()));
		buttonPeriodTo.setText(df.format(cTo.getTime()));
	}

    private void selectPeriod(PeriodType periodType) {
        disableButtons();
        updateDate(periodType.calculatePeriod(this));
    }

	protected void selectCustom() {
		updateDate();
		enableButtons();				
	}

	private void reset() {
		spinnerPeriodType.setSelection(0);		
	}

}

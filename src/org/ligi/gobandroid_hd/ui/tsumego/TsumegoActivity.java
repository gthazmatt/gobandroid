package org.ligi.gobandroid_hd.ui.tsumego;

import java.util.Vector;

import org.ligi.android.common.dialogs.ActivityFinishOnDialogClickListener;
import org.ligi.android.common.dialogs.DialogDiscarder;
import org.ligi.gobandroid_hd.R;
import org.ligi.gobandroid_hd.logic.GoGame;
import org.ligi.gobandroid_hd.logic.GoGame.GoGameChangeListener;
import org.ligi.gobandroid_hd.logic.GoMove;
import org.ligi.gobandroid_hd.ui.GoActivity;
import org.ligi.gobandroid_hd.ui.review.SGFMetaData;
import org.ligi.tracedroid.logging.Log;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class TsumegoActivity extends GoActivity implements GoGameChangeListener {
	
	private GoMove finishing_move;

	private TsumegoGameExtrasFragment myTsumegoExtrasFragment;
	private Vector<GoMove> on_path_moves;
	
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setTitle(R.string.tsumego);
		
		// build a on path Vector to do a fast isOnPath() later 
		on_path_moves=new Vector<GoMove>();
		
		recursive_add_on_path_moves(game.getFirstMove());
				
		// try to find the correct solution
		if (!isFinishingMoveKnown()) 
			new AlertDialog.Builder(this).setMessage(R.string.tsumego_sgf_no_solution)
			.setNegativeButton("OK",new DialogDiscarder())
			.setPositiveButton("go back",new ActivityFinishOnDialogClickListener(this))
			.show();

		game.addGoGameChangeListener(this);
		
		float myZoom=TsumegoHelper.calcZoom(game);
		
		getBoard().setZoom(myZoom);
		getBoard().setZoomPOI(TsumegoHelper.calcPOI( game));
    }
    
    private GoMove getFinishingMove() {
    	if (finishing_move==null)
    		finishing_move=getCorrectMove(game.getFirstMove());
		
    	return finishing_move;
    }
    private boolean isFinishingMoveKnown() {
    	return getFinishingMove()!=null;
    }
      
    private void recursive_add_on_path_moves(GoMove act) {
    	on_path_moves.add(act);
    	if (act.hasNextMove())
    		for (GoMove child:act.getNextMoveVariations())
    			recursive_add_on_path_moves(child);
    }
    
    @Override
	protected void onDestroy() {
    	game.removeGoGameChangeListener(this);
    	super.onDestroy();
    }

	private boolean isOnPath() {
		return on_path_moves.contains(game.getActMove());
    }
    
	public byte doMoveWithUIFeedback(byte x,byte y) {
		
		byte res=super.doMoveWithUIFeedback(x,y);
		if (res==GoGame.MOVE_VALID)
			if (game.getActMove().hasNextMove()) {
				game.jump(game.getActMove().getnextMove(0));
			}

		game.notifyGameChange();
		return res;
	}


	private GoMove getCorrectMove(GoMove act_mve) {
		if (act_mve.getComment().trim().toUpperCase().startsWith("CORRECT") || // gogameguru style 
			//act_mve.getComment().trim().toUpperCase().startsWith("RIGHT") || // goproblem.com
			act_mve.getComment().contains("RIGHT")  // goproblem.com
				)
			return act_mve;
		
		for (GoMove next_moves:act_mve.getNextMoveVariations()) {
			GoMove res=getCorrectMove(next_moves);
			if (res!=null)
				return res;
		}
			
		return null;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	this.getSupportMenuInflater().inflate(R.menu.ingame_tsumego, menu);
    	menu.findItem(R.id.menu_game_hint).setVisible(isFinishingMoveKnown()&&isOnPath());
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {                                                                                                 
        if (!super.onOptionsItemSelected(item))
        switch (item.getItemId()) {                                                                                                                   
                                                                                                                                                      
        case R.id.menu_game_hint:
        	
        	TsumegoHintAlert.show(this,getFinishingMove());
            break;                                                                                                                                
		}
		
		return false;
	}

    @Override
    public void requestUndo() {
    	// we do not want to keep user-variations in tsumego mode- but we want to keep tsumego variation
    	game.undo(isOnPath());
    	
    	// remove the counter-move if any
    	if (!game.isBlackToMove())
    		game.undo(isOnPath());
    }
    
    @Override
	public Fragment getGameExtraFragment() {
    	//if(myTsumegoExtrasFragment==null)
    	myTsumegoExtrasFragment=new TsumegoGameExtrasFragment();

		return myTsumegoExtrasFragment;
	}

	@Override
	public void onGoGameChange() {
		super.onGoGameChange();
		if (myTsumegoExtrasFragment!=null) {
			myTsumegoExtrasFragment.setOffPathVisibility(!isOnPath());
			myTsumegoExtrasFragment.setCorrectVisibility(game.getActMove().equals(getFinishingMove()));
		}
		if (game.getActMove().equals(getFinishingMove())) {
			SGFMetaData meta=new SGFMetaData(game.getMetaData().getFileName());
			meta.setIsSolved(true);
			meta.persist();
			/*this.getBaseContext().getSharedPreferences("tsumego_stats", Activity.MODE_PRIVATE)
			.edit().putInt(game.getMetaData().getFileName(), 100).commit();*/
			Log.i("written finished"+	game.getMetaData().getFileName());
		}
		this.invalidateOptionsMenu();		
	}

	@Override
	public boolean isAsk4QuitEnabled() {
		return false;
	}


}

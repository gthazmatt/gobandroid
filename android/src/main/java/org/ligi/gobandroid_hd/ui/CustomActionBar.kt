package org.ligi.gobandroid_hd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.support.v4.content.res.ResourcesCompat
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.ligi.gobandroid_hd.App
import org.ligi.gobandroid_hd.InteractionScope
import org.ligi.gobandroid_hd.InteractionScope.Mode.*
import org.ligi.gobandroid_hd.R
import org.ligi.gobandroid_hd.events.GameChangedEvent
import org.ligi.gobandroid_hd.model.GameProvider
import org.ligi.gobandroid_hd.ui.gnugo.GnuGoHelper
import org.ligi.gobandroid_hd.ui.ingame_common.SwitchModeHelper
import org.ligi.tracedroid.logging.Log
import javax.inject.Inject

class CustomActionBar(private val activity: Activity) : LinearLayout(activity) {

    private val GooglePlayStorePackageNameOld = "com.google.market"
    private val GooglePlayStorePackageNameNew = "com.android.vending"

    @Inject
    lateinit internal var gameProvider: GameProvider

    @Inject
    lateinit internal var interactionScope: InteractionScope

    @BindView(R.id.white_captures_tv)
    lateinit internal var white_captures_tv: TextView

    @BindView(R.id.black_captures_tv)
    lateinit internal var black_captures_tv: TextView

    @BindView(R.id.move_tv)
    lateinit internal var move_tv: TextView

    @BindView(R.id.mode_tv)
    lateinit internal var mode_tv: TextView

    @BindView(R.id.blackStoneImageView)
    lateinit internal var black_info_container: View

    @BindView(R.id.whiteStoneImageview)
    lateinit internal var white_info_container: View


    @OnClick(R.id.mode_tv)
    internal fun onModeSpinnerClick() {
        showModePopup(activity)
    }

    @OnClick(R.id.move_tv)
    internal fun onMoveSpinnerClick() {
        showModePopup(activity)
    }

    private val inflater: LayoutInflater
    private val app: App

    private val highlight_color: Int
    private val transparent: Int

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    init {

        App.component().inject(this)
        app = activity.applicationContext as App


        highlight_color = ResourcesCompat.getColor(resources, R.color.dividing_color, null)
        transparent = ResourcesCompat.getColor(resources, android.R.color.transparent, null)

        inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val top_view = inflater.inflate(R.layout.top_nav_and_extras, this)

        ButterKnife.bind(this, top_view)
        refresh()
    }

    private fun addItem(container: LinearLayout, image_resId: Int, str_resid: Int, listener: Runnable) {

        val v = inflater.inflate(R.layout.dropdown_item, container, false)
        (v.findViewById(R.id.text) as TextView).setText(str_resid)
        (v.findViewById(R.id.image) as ImageView).setImageResource(image_resId)

        v.findViewById(R.id.click_container).setOnClickListener { listener.run() }
        container.addView(v)
    }

    private fun addModeItem(container: LinearLayout, mode: InteractionScope.Mode, string_res: Int, icon_res: Int, pop: BetterPopupWindow) {
        if (mode === interactionScope.mode) {
            return  // already in this mode - no need to present the user with this option
        }

        addItem(container, icon_res, string_res, Runnable {
            pop.dismiss()

            if (mode === InteractionScope.Mode.GNUGO && !GnuGoHelper.isGnuGoAvail(activity)) {
                AlertDialog.Builder(activity).setTitle(R.string.install_gnugo)
                        .setMessage(R.string.gnugo_not_installed)
                        .setPositiveButton(android.R.string.ok) { dialog, which ->
                            val intent = Intent(Intent.ACTION_VIEW);
                            intent.data = Uri.parse("market://details?id=org.ligi.gobandroidhd.ai.gnugo")
                            val chooser = Intent.createChooser(intent, null)
                            activity.startActivity(chooser)
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                return@Runnable
            }
            activity.finish()
            Log.i("set mode" + mode)
            interactionScope.mode = mode
            val i = SwitchModeHelper.getIntentByMode(app, mode)
            activity.startActivity(i)
        })
    }

    private fun showModePopup(ctx: Context) {

        val pop = BetterPopupWindow(mode_tv)

        val scrollView = ScrollView(ctx)
        val contentView = LinearLayout(ctx)
        contentView.orientation = LinearLayout.VERTICAL
        val background = BitmapDrawableNoMinimumSize(ctx.resources, R.drawable.wood_bg)
        contentView.setBackgroundDrawable(background)

        addModeItem(contentView, SETUP, R.string.setup, R.drawable.ic_action_settings_overscan, pop)

        addModeItem(contentView, RECORD, R.string.play, R.drawable.ic_social_people, pop)

        addModeItem(contentView, EDIT, R.string.edit, R.drawable.ic_editor_mode_edit, pop)

        val actMove = gameProvider.get().actMove
        if (actMove.hasNextMove() || actMove.parent != null)
            addModeItem(contentView, REVIEW, R.string.review, R.drawable.ic_maps_local_movies, pop)

        if (actMove.movePos > 0) {
            // these modes only make sense if there is minimum one
            addModeItem(contentView, COUNT, R.string.count, R.drawable.ic_editor_pie_chart, pop)
        }

        if (actMove.hasNextMove()) {
            addModeItem(contentView, TSUMEGO, R.string.tsumego, R.drawable.ic_action_extension, pop)
            addModeItem(contentView, TELEVIZE, R.string.televize, R.drawable.ic_notification_live_tv, pop)
        }

        if (isPlayStoreInstalled() || GnuGoHelper.isGnuGoAvail(activity)) {
            addModeItem(contentView, GNUGO, R.string.gnugo, R.drawable.ic_hardware_computer, pop)
        }

        scrollView.addView(contentView)
        pop.setContentView(scrollView)

        pop.showLikePopDownMenu()
    }

    @Subscribe
    fun onGoGameChaged(@Suppress("UNUSED_PARAMETER") event: GameChangedEvent) {
        refresh()
    }

    private fun refresh() {
        post {
            val actMode = interactionScope.mode

            mode_tv.setText(actMode.getStringRes())

            val game = gameProvider.get()

            white_captures_tv.text = game.capturesWhite.toString()
            black_captures_tv.text = game.capturesBlack.toString()

            val isWhitesMove = !game.isBlackToMove && !game.isFinished
            white_info_container.setBackgroundColor(if (isWhitesMove) transparent else highlight_color)
            white_captures_tv.setBackgroundColor(if (isWhitesMove) transparent else highlight_color)

            val isBlacksMove = game.isBlackToMove || game.isFinished
            black_info_container.setBackgroundColor(if (isBlacksMove) highlight_color else transparent)
            black_captures_tv.setBackgroundColor(if (isBlacksMove) highlight_color else transparent)

            move_tv.text = app.resources.getString(R.string.move) + game.actMove.movePos
        }
    }

    private fun isPlayStoreInstalled(): Boolean {
        val packageManager = app.packageManager
        val packages = packageManager.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)
        return packages.any() { it.packageName == GooglePlayStorePackageNameOld || it.packageName == GooglePlayStorePackageNameNew }
    }
}

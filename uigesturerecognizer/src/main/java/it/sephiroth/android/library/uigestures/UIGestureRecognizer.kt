package it.sephiroth.android.library.uigestures

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration

import java.util.ArrayList
import java.util.Collections

import it.sephiroth.android.library.simplelogger.LoggerFactory
import android.util.TypedValue


/**
 * AndroidGestureRecognizer is an Android implementation
 * of the Apple's UIGestureRecognizer framework. There's not guarantee, however, that
 * this library works 100% in the same way as the Apple version.<br></br>
 * This is the base class for all the UI gesture implementations.
 *
 * @author alessandro crugnola
 * @version 1.0.0
 * @see [
 * https://developer.apple.com/reference/uikit/uigesturerecognizer](https://developer.apple.com/reference/uikit/uigesturerecognizer)
 */

abstract class UIGestureRecognizer(var context: Context) : OnGestureRecognizerStateChangeListener {

    private val mStateListeners = Collections.synchronizedList(ArrayList<OnGestureRecognizerStateChangeListener>())

    private var mListener: OnActionListener? = null
    /**
     * @return The current recognizer internal state
     * @since 1.0.0
     */
    var state: State? = null
        protected set(state) {
            state?.let { logMessage(Log.INFO, "setState: %s", it) }

            val changed = this.state != state || state == State.Changed
            field = state

            if (changed) {
                val iterator = mStateListeners.listIterator()
                while (iterator.hasNext()) {
                    iterator.next().onStateChanged(this)
                }
            }
        }
    /**
     * @return True if the recognizer is enabled
     * @since 1.0.0
     */
    /**
     * Toggle the recognizer enabled state.
     *
     * @param enabled Set to false to prevent any motion event
     * to be intercepted by this recognizer
     * @since 1.0.0
     */
    var isEnabled: Boolean = false
    private var mBeganFiringEvents: Boolean = false
    /**
     * @see UIGestureRecognizer.setCancelsTouchesInView
     * @since 1.0.0
     */
    /**
     * @param value A Boolean value affecting whether touches are delivered to a view when a gesture is recognized
     * @see [
     * https://developer.apple.com/reference/uikit/uigesturerecognizer/1624218-cancelstouchesinview](https://developer.apple.com/reference/uikit/uigesturerecognizer/1624218-cancelstouchesinview)
     *
     * @since 1.0.0
     */
    var cancelsTouchesInView: Boolean = false

    internal var delegate: UIGestureRecognizerDelegate? = null
        set
    /**
     * @return current tag assigned to this instance
     * @since 1.0.0
     */
    /**
     * @param mTag custom object the instance should keep
     * @since 1.0.0
     */
    var tag: Any? = null
        set(mTag) {
            field = mTag

            if (sDebug) {
                logger.tag = mTag.toString()
            }
        }
    private var mId: Long = 0
    protected var requireFailureOf: UIGestureRecognizer? = null
        private set
    var lastEvent: MotionEvent? = null
        protected set
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    protected val mHandler: GestureHandler

    /**
     * @return Returns the number of touches involved in the gesture represented by the receiver.
     * @since 1.0.0
     */
    abstract val numberOfTouches: Int

    /**
     * @return Returns the X computed as the location in a given view of the gesture represented by the receiver.
     * @since 1.0.0
     */
    abstract val currentLocationX: Float

    /**
     * @return Returns the Y computed as the location in a given view of the gesture represented by the receiver.
     * @since 1.0.0
     */
    abstract val currentLocationY: Float

    protected val isListeningForOtherStateChanges: Boolean
        get() = null != requireFailureOf && requireFailureOf!!.hasOnStateChangeListenerListener(this)

    enum class State {
        Possible,
        Began,
        Changed,
        Failed,
        Cancelled,
        Ended
    }

    interface OnActionListener {
        fun onGestureRecognized(recognizer: UIGestureRecognizer)
    }

    init {
        mHandler = GestureHandler(Looper.getMainLooper())
        cancelsTouchesInView = true
        isEnabled = true
        mId = generateId()
    }

    private fun generateId(): Long {
        return id++.toLong()
    }

    @SuppressLint("HandlerLeak")
    protected inner class GestureHandler(mainLooper: Looper) : Handler(mainLooper) {

        override fun handleMessage(msg: Message) {
            this@UIGestureRecognizer.handleMessage(msg)
        }
    }

    /**
     * @return Has began firing events
     */
    open fun hasBeganFiringEvents(): Boolean {
        return mBeganFiringEvents
    }

    protected fun setBeginFiringEvents(value: Boolean) {
        mBeganFiringEvents = value
    }

    protected abstract fun removeMessages()

    protected fun removeMessages(vararg messages: Int) {
        for (message in messages) {
            mHandler.removeMessages(message)
        }
    }

    protected fun hasMessages(vararg messages: Int): Boolean {
        for (message in messages) {
            if (mHandler.hasMessages(message)) {
                return true
            }
        }
        return false
    }

    fun clearStateListeners() {
        mStateListeners.clear()
    }

    protected fun fireActionEvent() {
        logMessage(Log.INFO, "fireActionEvent: %s", this)
        mListener?.onGestureRecognized(this)
    }

    protected fun addOnStateChangeListenerListener(listener: OnGestureRecognizerStateChangeListener) {
        if (!mStateListeners.contains(listener)) {
            mStateListeners.add(listener)
        }
    }

    protected fun removeOnStateChangeListenerListener(listener: OnGestureRecognizerStateChangeListener): Boolean {
        return mStateListeners.remove(listener)
    }

    protected fun hasOnStateChangeListenerListener(listener: OnGestureRecognizerStateChangeListener): Boolean {
        return mStateListeners.contains(listener)
    }

    open fun onTouchEvent(event: MotionEvent): Boolean {
        lastEvent = MotionEvent.obtain(event)
        return false
    }

    protected abstract fun handleMessage(msg: Message)

    /**
     * @param mId change the instance id
     */
    fun setId(mId: Long) {
        this.mId = mId
    }

    fun setActionListener(listener: OnActionListener) {
        this.mListener = listener
    }

    fun inState(vararg states: State): Boolean {
        if (states.isEmpty()) {
            return false
        }
        for (state in states) {
            if (this.state == state) {
                return true
            }
        }
        return false
    }

    /**
     * @param other Creates a dependency relationship between the receiver and another gesture recognizer when the objects
     * are created
     * @see [
     * https://developer.apple.com/reference/uikit/uigesturerecognizer/1624203-require](https://developer.apple.com/reference/uikit/uigesturerecognizer/1624203-require)
     *
     * @since 1.0.0
     */
    fun requireFailureOf(other: UIGestureRecognizer?) {
        requireFailureOf?.removeOnStateChangeListenerListener(this)
        this.requireFailureOf = other
    }

    protected fun stopListenForOtherStateChanges() {
        requireFailureOf?.removeOnStateChangeListenerListener(this)
    }

    protected fun listenForOtherStateChanges() {
        requireFailureOf?.addOnStateChangeListenerListener(this)
    }

    override fun toString(): String {
        return javaClass.simpleName + "[state: " + state + ", tag:" + tag + "]"
    }

    protected fun logMessage(level: Int, fmt: String, vararg args: Any) {
        if (!sDebug) {
            return
        }

        when (level) {
            Log.INFO -> logger.info(fmt, *args)
            Log.DEBUG -> logger.debug(fmt, *args)
            Log.ASSERT, Log.ERROR -> logger.error(fmt, *args)
            Log.WARN -> logger.warn(fmt, *args)
            Log.VERBOSE -> logger.verbose(fmt, *args)
            else -> {
            }
        }
    }

    companion object {

        val VERSION = BuildConfig.VERSION_NAME
        /**
         * @return the instance id
         * @since 1.0.0
         */
        var id = 0
            private set
        internal var sDebug = false

        internal val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout().toLong()
        internal val TAP_TIMEOUT = ViewConfiguration.getTapTimeout().toLong()
        internal val DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout().toLong()
        internal val TOUCH_SLOP = 8
        internal val DOUBLE_TAP_SLOP = 100
        internal val DOUBLE_TAP_TOUCH_SLOP = TOUCH_SLOP

        fun setLogEnabled(enabled: Boolean) {
            sDebug = enabled
        }

        fun dp2pixels(context: Context, value: Float): Int {
            val r = context.resources
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, r.displayMetrics).toInt()
        }
    }
}

package it.sephiroth.android.library.uigestures

import android.content.Context
import android.graphics.PointF
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * UILongPressGestureRecognizer looks for long-press gestures. The user must
 * press one or more fingers on a view and hold them there for a minimum period of time before the action triggers. While down,
 * the user’s fingers may not move more than a specified distance; if they move beyond the specified distance, the gesture fails.
 *
 * @author alessandro crugnola
 * @see [
 * https://developer.apple.com/reference/uikit/uilongpressgesturerecognizer](https://developer.apple.com/reference/uikit/uilongpressgesturerecognizer)
 */
class UILongPressGestureRecognizer
/**
 * UILongPressGestureRecognizer looks for long-press gestures.
 * The user must press one or more fingers on a view and hold them there for a minimum period of time before the action
 * triggers.
 * While down, the user’s fingers may not move more than a specified distance; if they move beyond the specified distance,
 * the gesture fails.
 */
(context: Context) : UIGestureRecognizer(context), UIContinuousRecognizer {
    /**
     * @return The minimum period fingers must press on the view for the gesture to be recognized.
     * @since 1.0.0
     */
    /**
     * @param value The minimum period fingers must press on the view for the gesture to be recognized.<br></br>
     * Value is in milliseconds
     * @since 1.0.0
     */
    var minimumPressDuration = UIGestureRecognizer.TAP_TIMEOUT + UIGestureRecognizer.LONG_PRESS_TIMEOUT

    // number of required fingers (default is 1)
    private var mTouchesRequired = 1
    // number of required taps (default is 1)
    private var mTapsRequired = 0

    private var mAlwaysInTapRegion: Boolean = false
    private var mDownFocusX: Float = 0.toFloat()
    private var mDownFocusY: Float = 0.toFloat()
    private val mTouchSlopSquare: Float
    private var mAllowableMovementSquare: Float = 0.toFloat()
    private var mStarted: Boolean = false

    private var mNumTaps = 0
    override var numberOfTouches = 0
        private set
    private val mCurrentLocation: PointF
    private var mBegan: Boolean = false

    override val currentLocationX: Float
        get() = mCurrentLocation.x

    override val currentLocationY: Float
        get() = mCurrentLocation.y

    /**
     * @return The maximum allowed movement of the fingers on the view before the gesture fails.
     * @since 1.0.0
     */
    /**
     * @param value The maximum movement of the fingers on the view before the gesture fails.
     * @since 1.0.0
     */
    var allowableMovement: Float
        get() = mAllowableMovementSquare
        set(value) {
            mAllowableMovementSquare = value * value
        }

    init {

        mStarted = false
        mBegan = false
        mCurrentLocation = PointF()

        val touchSlop: Int

        if (context == null) {

            touchSlop = ViewConfiguration.getTouchSlop()
        } else {
            val configuration = ViewConfiguration.get(context)
            touchSlop = configuration.scaledTouchSlop
        }
        mTouchSlopSquare = (touchSlop * touchSlop).toFloat()
        mAllowableMovementSquare = mTouchSlopSquare
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MESSAGE_RESET -> {
                logMessage(Log.INFO, "handleMessage(MESSAGE_RESET)")
                handleReset()
            }

            MESSAGE_FAILED -> {
                logMessage(Log.INFO, "handleMessage(MESSAGE_FAILED)")
                handleFailed()
            }

            MESSAGE_POINTER_UP -> {
                logMessage(Log.INFO, "handleMessage(MESSAGE_POINTER_UP)")
                numberOfTouches = msg.arg1
            }

            MESSAGE_LONG_PRESS -> {
                logMessage(Log.INFO, "handleMessage(MESSAGE_LONG_PRESS)")
                handleLongPress()
            }

            else -> {
            }
        }
    }

    override fun onStateChanged(recognizer: UIGestureRecognizer) {
        recognizer.state?.let { logMessage(Log.VERBOSE, "onStateChanged(%s, %s)", recognizer, it) }
        state?.let { logMessage(Log.VERBOSE, "started: %b, state: %s", mStarted, it) }

        if (recognizer.state == UIGestureRecognizer.State.Failed && state == UIGestureRecognizer.State.Began) {
            stopListenForOtherStateChanges()
            fireActionEventIfCanRecognizeSimultaneously()

            if (mBegan && hasBeganFiringEvents()) {
                state = UIGestureRecognizer.State.Changed
            }

        } else if (recognizer.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended) && mStarted && inState(UIGestureRecognizer.State.Possible, UIGestureRecognizer.State.Began)) {
            stopListenForOtherStateChanges()
            removeMessages()
            state = UIGestureRecognizer.State.Failed
            setBeginFiringEvents(false)
            mStarted = false
        }
    }

    /**
     * @param value The number of required taps for this recognizer to succeed.<br></br>
     * Default value is 1
     * @since 1.0.0
     */
    fun setNumberOfTapsRequired(value: Int) {
        mTapsRequired = value
    }

    /**
     * @param value The number of required touches for this recognizer to succeed.<br></br>
     * Default value is 1
     * @since 1.0.0
     */
    fun setNumberOfTouchesRequired(value: Int) {
        mTouchesRequired = value
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)

        if (!isEnabled) {
            return false
        }

        val action = ev.action
        val count = ev.pointerCount

        val pointerUp = action == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) ev.actionIndex else -1

        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until count) {
            if (skipIndex == i) {
                continue
            }
            sumX += ev.getX(i)
            sumY += ev.getY(i)
        }

        val div = if (pointerUp) count - 1 else count
        val focusX = sumX / div
        val focusY = sumY / div
        mCurrentLocation.set(focusX, focusY)

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                removeMessages()

                mAlwaysInTapRegion = true
                numberOfTouches = count
                mBegan = false

                if (!mStarted) {
                    stopListenForOtherStateChanges()
                    state = UIGestureRecognizer.State.Possible
                    setBeginFiringEvents(false)
                    mNumTaps = 0
                    mStarted = true
                } else {
                    mNumTaps++
                }

                if (mNumTaps == mTapsRequired) {
                    mHandler.sendEmptyMessageAtTime(MESSAGE_LONG_PRESS, ev.downTime + minimumPressDuration)
                } else {
                    var timeout = UIGestureRecognizer.LONG_PRESS_TIMEOUT
                    if (timeout >= minimumPressDuration) {
                        timeout = minimumPressDuration - 1
                    }
                    mHandler.sendEmptyMessageDelayed(MESSAGE_FAILED, timeout)
                }

                mDownFocusX = focusX
                mDownFocusY = focusY
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (state == UIGestureRecognizer.State.Possible && mStarted) {
                removeMessages(MESSAGE_POINTER_UP)
                numberOfTouches = count

                if (numberOfTouches > 1) {
                    if (numberOfTouches > mTouchesRequired) {
                        removeMessages()
                        state = UIGestureRecognizer.State.Failed
                    }
                }

                mDownFocusX = focusX
                mDownFocusY = focusY
            } else if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed) && mStarted) {
                numberOfTouches = count
            }

            MotionEvent.ACTION_POINTER_UP -> if (state == UIGestureRecognizer.State.Possible && mStarted) {
                removeMessages(MESSAGE_POINTER_UP)

                mDownFocusX = focusX
                mDownFocusY = focusY

                val message = mHandler.obtainMessage(MESSAGE_POINTER_UP)
                message.arg1 = numberOfTouches - 1
                mHandler.sendMessageDelayed(message, UIGestureRecognizer.TAP_TIMEOUT)
            } else if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)) {
                if (numberOfTouches - 1 < mTouchesRequired) {
                    val began = hasBeganFiringEvents()
                    state = UIGestureRecognizer.State.Ended

                    if (began) {
                        fireActionEvent()
                    }

                    setBeginFiringEvents(false)
                }
            }

            MotionEvent.ACTION_MOVE -> if (state == UIGestureRecognizer.State.Possible && mStarted) {

                if (mAlwaysInTapRegion) {
                    val deltaX = focusX - mDownFocusX
                    val deltaY = focusY - mDownFocusY
                    val distance = deltaX * deltaX + deltaY * deltaY

                    if (distance > mAllowableMovementSquare) {
                        logMessage(Log.WARN, "moved too much!: $distance")
                        mAlwaysInTapRegion = false
                        removeMessages()
                        state = UIGestureRecognizer.State.Failed
                    }
                }
            } else if (state == UIGestureRecognizer.State.Began) {
                if (!mBegan) {
                    val deltaX = focusX - mDownFocusX
                    val deltaY = focusY - mDownFocusY
                    val distance = deltaX * deltaX + deltaY * deltaY

                    if (distance > mTouchSlopSquare) {
                        mBegan = true

                        if (hasBeganFiringEvents()) {
                            state = UIGestureRecognizer.State.Changed
                            fireActionEvent()
                        }
                    }
                }
            } else if (state == UIGestureRecognizer.State.Changed) {
                state = UIGestureRecognizer.State.Changed
                if (hasBeganFiringEvents()) {
                    fireActionEvent()
                }
            }

            MotionEvent.ACTION_UP -> {
                removeMessages(MESSAGE_RESET, MESSAGE_POINTER_UP, MESSAGE_LONG_PRESS)

                if (state == UIGestureRecognizer.State.Possible && mStarted) {
                    if (numberOfTouches != mTouchesRequired) {
                        mStarted = false
                        removeMessages()
                        state = UIGestureRecognizer.State.Failed
                        postReset()
                    } else {
                        if (mNumTaps < mTapsRequired) {
                            removeMessages(MESSAGE_FAILED)
                            delayedFail()
                        } else {
                            mNumTaps = 0
                            mStarted = false
                            removeMessages()
                            state = UIGestureRecognizer.State.Failed
                        }
                    }
                } else if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)) {
                    mNumTaps = 0
                    mStarted = false
                    val began = hasBeganFiringEvents()
                    state = UIGestureRecognizer.State.Ended
                    if (began) {
                        fireActionEvent()
                    }
                    postReset()
                } else {
                    mStarted = false
                    postReset()
                }
                setBeginFiringEvents(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                removeMessages()
                mStarted = false
                mNumTaps = 0
                state = UIGestureRecognizer.State.Cancelled
                postReset()
            }

            else -> {
            }
        }

        return cancelsTouchesInView
    }

    override fun removeMessages() {
        removeMessages(MESSAGE_FAILED, MESSAGE_RESET, MESSAGE_POINTER_UP, MESSAGE_LONG_PRESS)
    }

    private fun postReset() {
        mHandler.sendEmptyMessage(MESSAGE_RESET)
    }

    private fun delayedFail() {
        mHandler.sendEmptyMessageDelayed(MESSAGE_FAILED, UIGestureRecognizer.DOUBLE_TAP_TIMEOUT)
    }

    private fun handleFailed() {
        removeMessages()
        state = UIGestureRecognizer.State.Failed
        setBeginFiringEvents(false)

        mStarted = false
    }

    private fun handleReset() {
        state = UIGestureRecognizer.State.Possible
        setBeginFiringEvents(false)
        mStarted = false
    }

    private fun handleLongPress() {
        logMessage(Log.INFO, "handleLongPress")

        removeMessages(MESSAGE_FAILED)

        if (state == UIGestureRecognizer.State.Possible && mStarted) {
            if (numberOfTouches == mTouchesRequired && delegate!!.shouldBegin(this)) {
                state = UIGestureRecognizer.State.Began
                if (null == requireFailureOf) {
                    fireActionEventIfCanRecognizeSimultaneously()
                } else {
                    if (requireFailureOf!!.state == UIGestureRecognizer.State.Failed) {
                        fireActionEventIfCanRecognizeSimultaneously()
                    } else if (requireFailureOf!!.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed, UIGestureRecognizer.State.Ended)) {
                        state = UIGestureRecognizer.State.Failed
                        setBeginFiringEvents(false)
                        mStarted = false
                        mNumTaps = 0
                    } else {
                        listenForOtherStateChanges()
                        setBeginFiringEvents(false)
                        logMessage(Log.DEBUG, "waiting...")
                    }
                }
            } else {
                state = UIGestureRecognizer.State.Failed
                setBeginFiringEvents(false)
                mStarted = false
                mNumTaps = 0
            }
        }
    }

    private fun fireActionEventIfCanRecognizeSimultaneously() {
        if (inState(UIGestureRecognizer.State.Changed, UIGestureRecognizer.State.Ended)) {
            setBeginFiringEvents(true)
            fireActionEvent()
        } else {
            if (delegate!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(this)) {
                setBeginFiringEvents(true)
                fireActionEvent()
            }
        }
    }

    override fun hasBeganFiringEvents(): Boolean {
        return super.hasBeganFiringEvents() && inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)
    }

    companion object {

        // request to change the current state to Failed
        private val MESSAGE_FAILED = 1
        // request to change the current state to Possible
        private val MESSAGE_RESET = 2
        // we handle the action_pointer_up received in the onTouchEvent with a delay
        // in order to check how many fingers were actually down when we're checking them
        // in the action_up.
        private val MESSAGE_POINTER_UP = 3
        // post handle the long press event
        private val MESSAGE_LONG_PRESS = 4
    }

}

package it.sephiroth.android.library.uigestures

import android.content.Context
import android.graphics.PointF
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * UITapGestureRecognizer looks for single or multiple taps.
 * For the gesture to be recognized, the specified number of fingers must tap the view a specified number of times.
 *
 * @author alessandro crugnola
 * @see [
 * https://developer.apple.com/reference/uikit/uitapgesturerecognizer](https://developer.apple.com/reference/uikit/uitapgesturerecognizer)
 */
/**
 * UITapGestureRecognizer looks for single or multiple taps. For the
 * gesture to be recognized, the specified number of fingers must tap the view a specified number of times.
 */
class UITapGestureRecognizer(context: Context) : UIGestureRecognizer(context), UIDiscreteGestureRecognizer {

    private val mDoubleTapTouchSlopSquare: Int

    // number of required fingers (default is 1)
    private var mTouchesRequired = 1
    // number of required taps (default is 1)
    private var mTapsRequired = 1

    private var mAlwaysInTapRegion: Boolean = false
    private var mDownFocusX: Float = 0.toFloat()
    private var mDownFocusY: Float = 0.toFloat()
    private val mTouchSlopSquare: Int
    private var mStarted: Boolean = false

    private var mNumTaps = 0
    override var numberOfTouches = 0
        private set
    private var mTapTimeout: Long = 0
    private val mCurrentLocation: PointF

    override val currentLocationX: Float
        get() = mCurrentLocation.x

    override val currentLocationY: Float
        get() = mCurrentLocation.y

    init {

        mStarted = false
        mCurrentLocation = PointF()

        val configuration = ViewConfiguration.get(context)
        val touchSlop: Int = configuration.scaledTouchSlop
        val doubleTapTouchSlop: Int = UIGestureRecognizer.DOUBLE_TAP_TOUCH_SLOP

        mTapTimeout = ViewConfiguration.getTapTimeout().toLong()
        mTouchSlopSquare = touchSlop * touchSlop
        mDoubleTapTouchSlopSquare = doubleTapTouchSlop * doubleTapTouchSlop
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
                handleFailed()
            }

            else -> {
            }
        }
    }

    fun setTapTimeout(mTapTimeout: Long) {
        this.mTapTimeout = mTapTimeout
    }

    /**
     * Change the number of required taps for this recognizer to succeed.<br></br>
     * Default value is 1
     *
     * @since 1.0.0
     */
    fun setNumberOfTapsRequired(value: Int) {
        mTapsRequired = value
    }

    /**
     * Change the number of required touches for this recognizer to succeed.<br></br>
     * Default value is 1
     *
     * @since 1.0.0
     */
    fun setNumberOfTouchesRequired(value: Int) {
        mTouchesRequired = value
    }

    override fun onStateChanged(recognizer: UIGestureRecognizer) {
        if (UIGestureRecognizer.sDebug) {
            recognizer.state?.let { logMessage(Log.VERBOSE, "onStateChanged(%s): %s", recognizer, it) }
            state?.let { logMessage(Log.VERBOSE, "this.state: %s", it) }
            logMessage(Log.VERBOSE, "mStarted: %s", mStarted)
        }

        if (recognizer.state == UIGestureRecognizer.State.Failed && state == UIGestureRecognizer.State.Ended) {
            stopListenForOtherStateChanges()
            fireActionEventIfCanRecognizeSimultaneously()
            postReset()
        } else if (recognizer.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended) && mStarted && inState(UIGestureRecognizer.State.Possible, UIGestureRecognizer.State.Ended)) {
            stopListenForOtherStateChanges()
            removeMessages()
            state = UIGestureRecognizer.State.Failed
            mStarted = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)

        if (!isEnabled) {
            return false
        }

        val action = event.action
        val count = event.pointerCount

        val pointerUp = action == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1

        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until count) {
            if (skipIndex == i) {
                continue
            }
            sumX += event.getX(i)
            sumY += event.getY(i)
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

                state = UIGestureRecognizer.State.Possible
                setBeginFiringEvents(false)

                if (!mStarted) {
                    stopListenForOtherStateChanges()
                    mNumTaps = 0
                    mStarted = true
                }

                mHandler.sendEmptyMessageDelayed(MESSAGE_LONG_PRESS, mTapTimeout)

                mNumTaps++
                mDownFocusX = focusX
                mDownFocusY = focusY
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (state == UIGestureRecognizer.State.Possible && mStarted) {
                removeMessages(MESSAGE_POINTER_UP)

                numberOfTouches = count

                if (numberOfTouches > 1) {
                    if (numberOfTouches > mTouchesRequired) {
                        state = UIGestureRecognizer.State.Failed
                    }
                }
                mDownFocusX = focusX
                mDownFocusY = focusY
            }

            MotionEvent.ACTION_POINTER_UP -> if (state == UIGestureRecognizer.State.Possible && mStarted) {
                removeMessages(MESSAGE_FAILED, MESSAGE_RESET, MESSAGE_POINTER_UP)

                mDownFocusX = focusX
                mDownFocusY = focusY

                val message = mHandler.obtainMessage(MESSAGE_POINTER_UP)
                message.arg1 = numberOfTouches - 1
                mHandler.sendMessageDelayed(message, UIGestureRecognizer.TAP_TIMEOUT)
            }

            MotionEvent.ACTION_MOVE -> if (state == UIGestureRecognizer.State.Possible && mStarted) {
                if (mAlwaysInTapRegion) {
                    val deltaX = (focusX - mDownFocusX).toInt()
                    val deltaY = (focusY - mDownFocusY).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY

                    val slop = if (mTapsRequired > 1) mDoubleTapTouchSlopSquare else mTouchSlopSquare

                    if (distance > slop) {
                        logMessage(Log.WARN, "moved too much!")
                        mAlwaysInTapRegion = false

                        removeMessages()
                        state = UIGestureRecognizer.State.Failed
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                removeMessages(MESSAGE_RESET, MESSAGE_POINTER_UP, MESSAGE_LONG_PRESS)

                if (state == UIGestureRecognizer.State.Possible && mStarted) {
                    if (numberOfTouches != mTouchesRequired) {
                        handleFailed()
                    } else {
                        if (mNumTaps < mTapsRequired) {
                            delayedFail()
                        } else {
                            // nailed!
                            if (delegate!!.shouldBegin(this)) {
                                state = UIGestureRecognizer.State.Ended

                                if (null == requireFailureOf) {
                                    fireActionEventIfCanRecognizeSimultaneously()
                                    postReset()
                                } else {
                                    if (requireFailureOf!!.state == UIGestureRecognizer.State.Failed) {
                                        fireActionEventIfCanRecognizeSimultaneously()
                                        postReset()
                                    } else if (requireFailureOf!!.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended, UIGestureRecognizer.State.Changed)) {
                                        state = UIGestureRecognizer.State.Failed
                                    } else {
                                        listenForOtherStateChanges()
                                        logMessage(Log.DEBUG, "waiting...")
                                    }
                                }
                            } else {
                                state = UIGestureRecognizer.State.Failed
                            }
                            mStarted = false
                        }
                    }
                } else {
                    handleReset()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                removeMessages()
                mStarted = false
                state = UIGestureRecognizer.State.Cancelled
                setBeginFiringEvents(false)
                postReset()
            }

            else -> {
            }
        }
        return cancelsTouchesInView
    }

    private fun fireActionEventIfCanRecognizeSimultaneously() {
        if (delegate!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(this)) {
            setBeginFiringEvents(true)
            fireActionEvent()
        }
    }

    override fun hasBeganFiringEvents(): Boolean {
        return super.hasBeganFiringEvents() && inState(UIGestureRecognizer.State.Ended)
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
        state = UIGestureRecognizer.State.Failed
        setBeginFiringEvents(false)
        removeMessages()
        mStarted = false
    }

    private fun handleReset() {
        state = UIGestureRecognizer.State.Possible
        setBeginFiringEvents(false)
        mStarted = false
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
        // a long press will make this gesture to fail
        private val MESSAGE_LONG_PRESS = 4

        private val TAG = UITapGestureRecognizer::class.java.simpleName
    }
}

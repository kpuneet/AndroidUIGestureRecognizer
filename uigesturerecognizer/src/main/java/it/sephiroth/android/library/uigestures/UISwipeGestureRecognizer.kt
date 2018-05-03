package it.sephiroth.android.library.uigestures

import android.content.Context
import android.graphics.PointF
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration

/**
 * UISwipeGestureRecognizer is a subclass of UIGestureRecognizer that looks for swiping gestures in one or more
 * directions. A swipe is a discrete gesture, and thus the associated action message is sent only once per gesture.
 *
 * @author alessandro crugnola
 * @see [
 * https://developer.apple.com/reference/uikit/uiswipegesturerecognizer](https://developer.apple.com/reference/uikit/uiswipegesturerecognizer)
 */

class UISwipeGestureRecognizer(context: Context) : UIGestureRecognizer(context), UIDiscreteGestureRecognizer {

    private val mTouchSlopSquare: Int
    private val mMaximumFlingVelocity: Int

    private var mStarted: Boolean = false
    /**
     * @return
     * @since 1.0.0
     */
    /**
     * @param direction
     * @since 1.0.0
     */
    var direction: Int = 0
    /**
     * @return
     * @since 1.0.0
     */
    /**
     * @param value
     * @since 1.0.0
     */
    var numberOfTouchesRequired: Int = 0

    private var mLastFocusX: Float = 0.toFloat()
    private var mLastFocusY: Float = 0.toFloat()
    private var mDownFocusX: Float = 0.toFloat()
    private var mDownFocusY: Float = 0.toFloat()

    private var mVelocityTracker: VelocityTracker? = null
    private var scrollX: Float = 0.toFloat()
    private var scrollY: Float = 0.toFloat()
    /**
     * @return
     * @since 1.0.0
     */
    /**
     * @param mTranslationX
     * @since 1.0.0
     */
    var translationX: Float = 0.toFloat()
    /**
     * @return
     * @since 1.0.0
     */
    /**
     * @param mTranslationY
     * @since 1.0.0
     */
    var translationY: Float = 0.toFloat()
    /**
     * @return
     * @since 1.0.0
     */
    var yVelocity: Float = 0.toFloat()
        private set
    /**
     * @return
     * @since 1.0.0
     */
    var xVelocity: Float = 0.toFloat()
        private set
    private val mCurrentLocation: PointF
    private var mDownTime: Long = 0
    override var numberOfTouches: Int = 0
        private set
    private var mDown: Boolean = false

    override val currentLocationX: Float
        get() = mCurrentLocation.x

    override val currentLocationY: Float
        get() = mCurrentLocation.y

    init {
        numberOfTouchesRequired = 1
        direction = RIGHT
        mStarted = false
        numberOfTouches = 0

        val touchSlop: Int
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        mMaximumFlingVelocity = configuration.scaledMaximumFlingVelocity
        mTouchSlopSquare = touchSlop * touchSlop
        mCurrentLocation = PointF()
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MESSAGE_RESET -> {
                mStarted = false
                setBeginFiringEvents(false)
                state = UIGestureRecognizer.State.Possible
            }
            else -> {
            }
        }
    }

    override fun removeMessages() {
        removeMessages(MESSAGE_RESET)
    }

    override fun onStateChanged(recognizer: UIGestureRecognizer) {
        recognizer.state?.let { logMessage(Log.VERBOSE, "onStateChanged(%s, %s)", recognizer, it) }
        state?.let { logMessage(Log.VERBOSE, "started: %b, state: %s", mStarted, it) }

        if (recognizer.state == UIGestureRecognizer.State.Failed && state == UIGestureRecognizer.State.Ended) {
            removeMessages()
            stopListenForOtherStateChanges()
            fireActionEventIfCanRecognizeSimultaneously()

            if (!mDown) {
                mStarted = false
                state = UIGestureRecognizer.State.Possible
            }

        } else if (recognizer.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended) && mStarted && inState(UIGestureRecognizer.State.Possible, UIGestureRecognizer.State.Ended)) {
            mStarted = false
            setBeginFiringEvents(false)
            stopListenForOtherStateChanges()
            removeMessages()
            state = UIGestureRecognizer.State.Failed
        }
    }

    private fun fireActionEventIfCanRecognizeSimultaneously() {
        if (delegate!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(this)) {
            setBeginFiringEvents(true)
            fireActionEvent()
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)

        if (!isEnabled) {
            return false
        }

        val action = ev.action

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }

        mVelocityTracker!!.addMovement(ev)

        val pointerUp = action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) ev.actionIndex else -1

        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        val count = ev.pointerCount
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

        mCurrentLocation.x = focusX
        mCurrentLocation.y = focusY

        numberOfTouches = if (pointerUp) count - 1 else count

        when (action and MotionEvent.ACTION_MASK) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                mLastFocusX = focusX
                mLastFocusY = focusY

                if (state == UIGestureRecognizer.State.Possible && !mStarted) {
                    if (count > numberOfTouchesRequired) {
                        state = UIGestureRecognizer.State.Failed
                        removeMessages(MESSAGE_RESET)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                mLastFocusX = focusX
                mLastFocusY = focusY

                mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                val upIndex = ev.actionIndex

                val id1 = ev.getPointerId(upIndex)
                val x1 = mVelocityTracker!!.getXVelocity(id1)
                val y1 = mVelocityTracker!!.getYVelocity(id1)
                for (i in 0 until count) {
                    if (i == upIndex) {
                        continue
                    }

                    val id2 = ev.getPointerId(i)
                    val x = x1 * mVelocityTracker!!.getXVelocity(id2)
                    val y = y1 * mVelocityTracker!!.getYVelocity(id2)

                    val dot = x + y

                    if (dot < 0) {
                        mVelocityTracker!!.clear()
                        break
                    }
                }

                if (state == UIGestureRecognizer.State.Possible && !mStarted) {
                    if (count - 1 < numberOfTouchesRequired) {
                        state = UIGestureRecognizer.State.Failed
                        removeMessages(MESSAGE_RESET)
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                mStarted = false
                mDown = true

                mLastFocusX = focusX
                mDownFocusX = mLastFocusX
                mLastFocusY = focusY
                mDownFocusY = mLastFocusY
                mDownTime = ev.eventTime

                mVelocityTracker!!.clear()

                setBeginFiringEvents(false)
                removeMessages(MESSAGE_RESET)
                state = UIGestureRecognizer.State.Possible
            }

            MotionEvent.ACTION_MOVE -> {
                scrollX = mLastFocusX - focusX
                scrollY = mLastFocusY - focusY

                if (state == UIGestureRecognizer.State.Possible) {
                    val deltaX = (focusX - mDownFocusX).toInt()
                    val deltaY = (focusY - mDownFocusY).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY
                    if (!mStarted) {
                        if (distance > mTouchSlopSquare) {
                            mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                            yVelocity = mVelocityTracker!!.yVelocity
                            xVelocity = mVelocityTracker!!.xVelocity

                            translationX -= scrollX
                            translationY -= scrollY

                            mLastFocusX = focusX
                            mLastFocusY = focusY
                            mStarted = true

                            if (count == numberOfTouchesRequired) {
                                val time = ev.eventTime - ev.downTime
                                if (time > MAXIMUM_TOUCH_SLOP_TIME) {
                                    logMessage(Log.WARN, "passed too much time")
                                    mStarted = false
                                    setBeginFiringEvents(false)
                                    state = UIGestureRecognizer.State.Failed
                                } else {
                                    val direction = getTouchDirection(mDownFocusX, mDownFocusY, focusX, focusY, xVelocity, yVelocity, 0f)
                                    logMessage(
                                            Log.VERBOSE,
                                            "time: " + (ev.eventTime - mDownTime) + " or " + (ev.eventTime - ev.downTime)
                                    )
                                    logMessage(Log.VERBOSE, "direction: $direction")

                                    if (direction == -1 || this.direction and direction == 0) {
                                        mStarted = false
                                        setBeginFiringEvents(false)
                                        state = UIGestureRecognizer.State.Failed
                                    } else {
                                        logMessage(Log.DEBUG, "direction accepted: " + (this.direction and direction))
                                        mStarted = true
                                    }
                                }
                            } else {
                                mStarted = false
                                setBeginFiringEvents(false)
                                state = UIGestureRecognizer.State.Failed
                            }
                        }
                    } else {
                        // touch has been recognized. now let's track the movement
                        mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumFlingVelocity.toFloat())
                        yVelocity = mVelocityTracker!!.yVelocity
                        xVelocity = mVelocityTracker!!.xVelocity
                        val time = ev.eventTime - ev.downTime

                        if (time > MAXIMUM_TOUCH_FLING_TIME) {
                            mStarted = false
                            state = UIGestureRecognizer.State.Failed
                        } else {
                            val direction = getTouchDirection(
                                    mDownFocusX, mDownFocusY, focusX, focusY, xVelocity, yVelocity, SWIPE_THRESHOLD.toFloat())

                            if (direction != -1) {
                                if (this.direction and direction != 0) {
                                    if (delegate!!.shouldBegin(this)) {
                                        state = UIGestureRecognizer.State.Ended
                                        if (null == requireFailureOf) {
                                            fireActionEventIfCanRecognizeSimultaneously()
                                        } else {
                                            if (requireFailureOf!!.state == UIGestureRecognizer.State.Failed) {
                                                fireActionEventIfCanRecognizeSimultaneously()
                                            } else if (requireFailureOf!!.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended, UIGestureRecognizer.State.Changed)) {
                                                mStarted = false
                                                setBeginFiringEvents(false)
                                                state = UIGestureRecognizer.State.Failed
                                            } else {
                                                logMessage(Log.DEBUG, "waiting...")
                                                listenForOtherStateChanges()
                                                setBeginFiringEvents(false)
                                            }
                                        }
                                    } else {
                                        state = UIGestureRecognizer.State.Failed
                                        mStarted = false
                                        setBeginFiringEvents(false)
                                    }
                                } else {
                                    mStarted = false
                                    setBeginFiringEvents(false)
                                    state = UIGestureRecognizer.State.Failed
                                }
                            }
                        }

                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mVelocityTracker != null) {
                    mVelocityTracker!!.recycle()
                    mVelocityTracker = null
                }

                // TODO: should we fail if the gesture didn't actually start?

                mDown = false
                removeMessages(MESSAGE_RESET)
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mVelocityTracker != null) {
                    mVelocityTracker!!.recycle()
                    mVelocityTracker = null
                }

                mDown = false
                removeMessages(MESSAGE_RESET)
                state = UIGestureRecognizer.State.Cancelled
                mHandler.sendEmptyMessage(MESSAGE_RESET)
            }

            else -> {
            }
        }

        return cancelsTouchesInView
    }

    private fun getTouchDirection(
            x1: Float, y1: Float, x2: Float, y2: Float, velocityX: Float, velocityY: Float, distanceThreshold: Float): Int {
        val diffY = y2 - y1
        val diffX = x2 - x1
        logMessage(Log.VERBOSE, "diff: %gx%g", diffX, diffY)
        logMessage(Log.VERBOSE, "velocity: %gx%g", velocityX, velocityY)

        if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > distanceThreshold && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                return if (diffX > 0) {
                    RIGHT
                } else {
                    LEFT
                }
            }
        } else if (Math.abs(diffY) > distanceThreshold && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
            return if (diffY > 0) {
                DOWN
            } else {
                UP
            }
        }
        return -1
    }

    /**
     * @return
     * @since 1.0.0
     */
    fun getScrollX(): Float {
        return -scrollX
    }

    /**
     * @return
     * @since 1.0.0
     */
    fun getScrollY(): Float {
        return -scrollY
    }

    companion object {
        private val MESSAGE_RESET = 4

        val RIGHT = 1 shl 1
        val LEFT = 1 shl 2
        val UP = 1 shl 3
        val DOWN = 1 shl 4

        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100
        val MAXIMUM_TOUCH_SLOP_TIME = 150
        val MAXIMUM_TOUCH_FLING_TIME = 300
    }
}
package it.sephiroth.android.library.uigestures

import android.content.Context
import android.graphics.PointF
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration

/**
 * UIPanGestureRecognizer is a subclass of UIGestureRecognizer that looks for panning (dragging) gestures. The user must
 * be pressing one or more fingers on a view while they pan it. Clients implementing the action method for this gesture
 * recognizer can ask it for the current translation and velocity of the gesture.
 *
 * @author alessandro crugnola
 * @see [
 * https://developer.apple.com/reference/uikit/uipangesturerecognizer](https://developer.apple.com/reference/uikit/uipangesturerecognizer)
 */
class UIScreenEdgePanGestureRecognizer(context: Context) : UIGestureRecognizer(context), UIContinuousRecognizer {
    private val mEdgeLimit: Float
    private val mTouchSlopSquare: Int

    private var mStarted: Boolean = false

    private var mLastFocusX: Float = 0.toFloat()
    private var mLastFocusY: Float = 0.toFloat()
    private var mDownFocusX: Float = 0.toFloat()
    private var mDownFocusY: Float = 0.toFloat()

    private var mVelocityTracker: VelocityTracker? = null
    /**
     * @since 1.0.0
     */
    /**
     * The minimum number of fingers that can be touching the view for this gesture to be recognized.
     * The default value is 1
     *
     * @since 1.0.0
     */
    var minimumNumberOfTouches: Int = 0
    /**
     * @since 1.0.0
     */
    /**
     * @param touches The maximum number of fingers that can be touching the view for this gesture to be recognized.
     * @since 1.0.0
     */
    var maximumNumberOfTouches: Int = 0
    private var scrollX: Float = 0.toFloat()
    private var scrollY: Float = 0.toFloat()
    /**
     * @return The translation X of the pan gesture
     * @since 1.0.0
     */
    /**
     * @param mTranslationX Change the current translation x
     * @since 1.0.0
     */
    var translationX: Float = 0.toFloat()
    /**
     * @return The translation Y of the pan gesture
     * @since 1.0.0
     */
    /**
     * @param mTranslationY Change the current translation y
     * @since 1.0.0
     */
    var translationY: Float = 0.toFloat()
    /**
     * @return The y velocity of the pan gesture
     * @since 1.0.0
     */
    var yVelocity: Float = 0.toFloat()
        private set
    /**
     * @return The x velocity of the pan gesture
     * @since 1.0.0
     */
    var xVelocity: Float = 0.toFloat()
        private set
    private val mCurrentLocation: PointF
    override var numberOfTouches: Int = 0
        private set
    var edge = UIRectEdge.LEFT

    override val currentLocationX: Float
        get() = mCurrentLocation.x

    override val currentLocationY: Float
        get() = mCurrentLocation.y

    init {
        minimumNumberOfTouches = 1
        maximumNumberOfTouches = Integer.MAX_VALUE

        val touchSlop: Int
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        mTouchSlopSquare = touchSlop * touchSlop
        mCurrentLocation = PointF()
        mEdgeLimit = context.resources.getDimension(R.dimen.gestures_screen_edge_limit)
        logMessage(Log.VERBOSE, "edge limits: %g", mEdgeLimit)
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

    override fun onStateChanged(recognizer: UIGestureRecognizer) {
        if (recognizer.state === UIGestureRecognizer.State.Failed && state === UIGestureRecognizer.State.Began) {
            stopListenForOtherStateChanges()
            fireActionEventIfCanRecognizeSimultaneously()

        } else if (recognizer.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended) && mStarted && inState(UIGestureRecognizer.State.Possible, UIGestureRecognizer.State.Began)) {
            stopListenForOtherStateChanges()
            removeMessages()
            state = UIGestureRecognizer.State.Failed
            setBeginFiringEvents(false)
            mStarted = false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)

        if (!isEnabled || null == delegate) {
            return false
        }

        val action = ev.action

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }

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

        val rawX = ev.rawX
        val rawY = ev.rawY

        numberOfTouches = count

        when (action and MotionEvent.ACTION_MASK) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                mLastFocusX = focusX
                mDownFocusX = mLastFocusX
                mLastFocusY = focusY
                mDownFocusY = mLastFocusY

                if (state === UIGestureRecognizer.State.Possible) {
                    if (count > maximumNumberOfTouches) {
                        state = UIGestureRecognizer.State.Failed
                        removeMessages(MESSAGE_RESET)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                mLastFocusX = focusX
                mDownFocusX = mLastFocusX
                mLastFocusY = focusY
                mDownFocusY = mLastFocusY
                numberOfTouches = count - 1

                mVelocityTracker!!.computeCurrentVelocity(1000, 0f)
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

                if (state === UIGestureRecognizer.State.Possible) {
                    if (count - 1 < minimumNumberOfTouches) {
                        state = UIGestureRecognizer.State.Failed
                        removeMessages(MESSAGE_RESET)
                    }
                }
            }

            MotionEvent.ACTION_DOWN -> {
                mLastFocusX = focusX
                mDownFocusX = mLastFocusX
                mLastFocusY = focusY
                mDownFocusY = mLastFocusY

                mVelocityTracker!!.clear()
                mVelocityTracker!!.addMovement(ev)

                mStarted = false

                stopListenForOtherStateChanges()
                removeMessages(MESSAGE_RESET)

                if (!computeState(rawX, rawY)) {
                    state = UIGestureRecognizer.State.Failed
                } else {
                    state = UIGestureRecognizer.State.Possible
                }

                setBeginFiringEvents(false)
            }

            MotionEvent.ACTION_MOVE -> {
                scrollX = mLastFocusX - focusX
                scrollY = mLastFocusY - focusY

                mVelocityTracker!!.addMovement(ev)

                if (state === UIGestureRecognizer.State.Possible && !mStarted) {
                    val deltaX = (focusX - mDownFocusX).toInt()
                    val deltaY = (focusY - mDownFocusY).toInt()
                    val distance = deltaX * deltaX + deltaY * deltaY
                    if (distance > mTouchSlopSquare) {

                        mVelocityTracker!!.computeCurrentVelocity(1000, java.lang.Float.MAX_VALUE)
                        yVelocity = mVelocityTracker!!.yVelocity
                        xVelocity = mVelocityTracker!!.xVelocity

                        translationX -= scrollX
                        translationY -= scrollY

                        mLastFocusX = focusX
                        mLastFocusY = focusY
                        mStarted = true

                        if (count >= minimumNumberOfTouches && count <= maximumNumberOfTouches
                                && delegate!!.shouldBegin(this)
                                && getTouchDirection(mDownFocusX, mDownFocusY, focusX, focusY, xVelocity, yVelocity) === edge) {

                            state = UIGestureRecognizer.State.Began

                            if (null == requireFailureOf) {
                                fireActionEventIfCanRecognizeSimultaneously()
                            } else {
                                if (requireFailureOf!!.state === UIGestureRecognizer.State.Failed) {
                                    fireActionEventIfCanRecognizeSimultaneously()
                                } else if (requireFailureOf!!.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended, UIGestureRecognizer.State.Changed)) {
                                    state = UIGestureRecognizer.State.Failed
                                } else {
                                    listenForOtherStateChanges()
                                    setBeginFiringEvents(false)
                                    logMessage(Log.DEBUG, "waiting...")
                                }
                            }
                        } else {
                            state = UIGestureRecognizer.State.Failed
                        }
                    }
                } else if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)) {
                    //if ((Math.abs(scrollX) >= 1 || Math.abs(scrollY) >= 1)) {
                    translationX -= scrollX
                    translationY -= scrollY

                    val pointerId = ev.getPointerId(0)
                    mVelocityTracker!!.computeCurrentVelocity(1000, java.lang.Float.MAX_VALUE)
                    yVelocity = mVelocityTracker!!.getYVelocity(pointerId)
                    xVelocity = mVelocityTracker!!.getXVelocity(pointerId)

                    if (hasBeganFiringEvents()) {
                        state = UIGestureRecognizer.State.Changed
                        fireActionEvent()
                    }

                    mLastFocusX = focusX
                    mLastFocusY = focusY
                }
            }

            MotionEvent.ACTION_UP -> {

                if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)) {
                    val began = hasBeganFiringEvents()
                    state = UIGestureRecognizer.State.Ended
                    if (began) {
                        fireActionEvent()
                    }
                }

                if (state === UIGestureRecognizer.State.Possible || !mStarted) {
                    yVelocity = 0f
                    xVelocity = yVelocity
                } else {
                    // TODO: verify this. it seems to send random values here
                    // VelocityTracker velocityTracker = mVelocityTracker;
                    // final int pointerId = ev.getPointerId(0);
                    // velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                    // mVelocityY = velocityTracker.getYVelocity(pointerId);
                    // mVelocityX = velocityTracker.getXVelocity(pointerId);
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker!!.recycle()
                    mVelocityTracker = null
                }

                mHandler.sendEmptyMessage(MESSAGE_RESET)
            }

            MotionEvent.ACTION_CANCEL -> {
                removeMessages(MESSAGE_RESET)
                state = UIGestureRecognizer.State.Cancelled
                setBeginFiringEvents(false)
                mHandler.sendEmptyMessage(MESSAGE_RESET)
            }

            else -> {
            }
        }

        return cancelsTouchesInView
    }

    private fun fireActionEventIfCanRecognizeSimultaneously() {
        if (inState(UIGestureRecognizer.State.Changed, UIGestureRecognizer.State.Ended)) {
            setBeginFiringEvents(true)
            fireActionEvent()
        } else {
            if (null != delegate) {
                if (delegate!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(this)) {
                    setBeginFiringEvents(true)
                    fireActionEvent()
                }
            }
        }
    }

    override fun hasBeganFiringEvents(): Boolean {
        return super.hasBeganFiringEvents() && inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)
    }

    /**
     * @return the relative scroll x between gestures
     * @since 1.0.0
     */
    fun getScrollX(): Float {
        return -scrollX
    }

    /**
     * @return the relative scroll y between gestures
     * @since 1.0.0
     */
    fun getScrollY(): Float {
        return -scrollY
    }

    override fun removeMessages() {
        removeMessages(MESSAGE_RESET)
    }

    private fun getTouchDirection(
            x1: Float, y1: Float, x2: Float, y2: Float, velocityX: Float, velocityY: Float): UIRectEdge {
        val diffY = y2 - y1
        val diffX = x2 - x1

        if (Math.abs(diffX) > Math.abs(diffY)) {
            if (Math.abs(diffX) > 0.toFloat()) {
                return if (diffX > 0) {
                    UIRectEdge.LEFT
                } else {
                    UIRectEdge.RIGTH
                }
            }
        } else if (Math.abs(diffY) > 0.toFloat()) {
            return if (diffY > 0) {
                UIRectEdge.TOP
            } else {
                UIRectEdge.BOTTOM
            }
        }
        return UIRectEdge.NONE
    }

    private fun computeState(x: Float, y: Float): Boolean {
        val context = context ?: return false

        if (edge === UIRectEdge.LEFT && x > mEdgeLimit) {
            return false
        } else if (edge === UIRectEdge.RIGTH) {
            val w = context.resources.displayMetrics.widthPixels
            return x >= w - mEdgeLimit
        } else if (edge === UIRectEdge.TOP && y > mEdgeLimit) {
            return false
        } else if (edge === UIRectEdge.BOTTOM) {
            val h = context.resources.displayMetrics.heightPixels
            return y >= h - mEdgeLimit
        } else if (edge === UIRectEdge.NONE) {
            return false
        }
        return true
    }

    companion object {
        private val MESSAGE_RESET = 4
    }
}
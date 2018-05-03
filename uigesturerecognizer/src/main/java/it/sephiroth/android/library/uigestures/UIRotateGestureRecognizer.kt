package it.sephiroth.android.library.uigestures

import android.content.Context
import android.graphics.PointF
import android.os.Message
import android.util.Log
import android.view.MotionEvent

/**
 * UIRotationGestureRecognizer is a subclass of UIGestureRecognizer that looks for rotation gestures involving two
 * touches. When the user moves the fingers opposite each other in a circular motion, the underlying view should rotate in a
 * corresponding direction and speed.
 *
 * @author alessandro crugnola
 * @see [
 * https://developer.apple.com/reference/uikit/uirotationgesturerecognizer](https://developer.apple.com/reference/uikit/uirotationgesturerecognizer)
 */

class UIRotateGestureRecognizer(context: Context) : UIGestureRecognizer(context), UIContinuousRecognizer {

    /**
     * @return the rotation threshold
     * @since 1.0.0
     */
    /**
     * Change the minimum rotation threshold (in radians)
     *
     * @param value threshold in radians
     * @since 1.0.0
     */
    var rotationThreshold: Double = 0.0
    /**
     * Returns the rotation in radians
     *
     * @return the current rotation in radians
     * @see .getRotationInDegrees
     * @since 1.0.0
     */
    var rotationInRadians: Float = 0F
        private set
    private var x1: Float = 0F
    private var y1: Float = 0F
    private var x2: Float = 0F
    private var y2: Float = 0F
    private var mPreviousAngle: Float = 0F
    /**
     * @return The velocity of the rotation gesture in radians per second.
     * @since 1.0.0
     */
    var velocity: Float = 0F
        private set
    private var mValid: Boolean = false
    private var mStarted: Boolean = false
    private var mPtrID1: Int = 0
    private var mPtrID2: Int = 0
    private var mPreviousEvent: MotionEvent? = null
    private val mCurrentLocation = PointF()
    override var numberOfTouches: Int = 0
        private set

    /**
     * Returns the rotation in degrees
     *
     * @return the current rotation in degrees
     * @see .getRotationInRadians
     * @since 1.0.0
     */
    val rotationInDegrees: Float
        get() {
            var angle = Math.toDegrees(rotationInRadians.toDouble()).toFloat() % 360
            if (angle < -180f) {
                angle += 360.0f
            }
            if (angle > 180f) {
                angle -= 360.0f
            }
            return angle
        }

    override val currentLocationX: Float
        get() = mCurrentLocation.x

    override val currentLocationY: Float
        get() = mCurrentLocation.y

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MESSAGE_RESET -> {
                stopListenForOtherStateChanges()
                state = UIGestureRecognizer.State.Possible
                setBeginFiringEvents(false)
            }
            else -> {
            }
        }
    }

    init {
        rotationThreshold = ROTATION_SLOP
        mPtrID1 = INVALID_POINTER_ID
        mPtrID2 = INVALID_POINTER_ID
        velocity = 0f
        numberOfTouches = 0
    }

    override fun onStateChanged(recognizer: UIGestureRecognizer) {
        recognizer.state?.let { logMessage(Log.VERBOSE, "onStateChanged(%s, %s)", recognizer, it) }
        state?.let { logMessage(Log.VERBOSE, "state: %s", it) }

        if (recognizer.state == UIGestureRecognizer.State.Failed && state == UIGestureRecognizer.State.Began) {
            stopListenForOtherStateChanges()
            fireActionEventIfCanRecognizeSimultaneously()

        } else if (recognizer.inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Ended) && inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Possible) && mStarted) {
            stopListenForOtherStateChanges()
            removeMessages()
            state = UIGestureRecognizer.State.Failed
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

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        super.onTouchEvent(ev)

        if (!isEnabled) {
            return false
        }

        val action = ev.action
        var count = ev.pointerCount

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
        numberOfTouches = if (pointerUp) count - 1 else count
        mCurrentLocation.set(focusX, focusY)

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mValid = false
                mStarted = false
                stopListenForOtherStateChanges()
                state = UIGestureRecognizer.State.Possible
                setBeginFiringEvents(false)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (count == 2 && state != UIGestureRecognizer.State.Failed) {
                    mPtrID1 = ev.getPointerId(0)
                    mPtrID2 = ev.getPointerId(1)

                    x1 = ev.getX(ev.findPointerIndex(mPtrID1))
                    y1 = ev.getY(ev.findPointerIndex(mPtrID1))
                    x2 = ev.getX(ev.findPointerIndex(mPtrID2))
                    y2 = ev.getY(ev.findPointerIndex(mPtrID2))
                    mValid = true
                } else {
                    mValid = false
                }

                mStarted = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                count -= 1

                if (count == 2 && state != UIGestureRecognizer.State.Failed) {

                    val pointerIndex = action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                    val pointerId = ev.getPointerId(pointerIndex)

                    var found = false
                    for (i in 0 until ev.pointerCount) {
                        val id = ev.getPointerId(i)
                        if (id == pointerId) {
                            continue
                        } else {
                            if (!found) {
                                mPtrID1 = id
                            } else {
                                mPtrID2 = id
                            }
                            found = true
                        }
                    }

                    x1 = ev.getX(ev.findPointerIndex(mPtrID1))
                    y1 = ev.getY(ev.findPointerIndex(mPtrID1))
                    x2 = ev.getX(ev.findPointerIndex(mPtrID2))
                    y2 = ev.getY(ev.findPointerIndex(mPtrID2))
                    mValid = true
                } else {
                    mValid = false
                }
                mStarted = false
            }

            MotionEvent.ACTION_UP -> {
                mValid = false
                mStarted = false
                mPreviousAngle = 0f
                velocity = 0f

                if (null != mPreviousEvent) {
                    mPreviousEvent!!.recycle()
                    mPreviousEvent = null
                }

                if (inState(UIGestureRecognizer.State.Began, UIGestureRecognizer.State.Changed)) {
                    state = UIGestureRecognizer.State.Ended
                    if (hasBeganFiringEvents()) {
                        fireActionEvent()
                    }
                    mHandler.sendEmptyMessage(MESSAGE_RESET)
                }
            }

            MotionEvent.ACTION_MOVE -> if (mValid && state != UIGestureRecognizer.State.Failed) {
                val nx1 = ev.getX(ev.findPointerIndex(mPtrID1))
                val ny1 = ev.getY(ev.findPointerIndex(mPtrID1))
                val nx2 = ev.getX(ev.findPointerIndex(mPtrID2))
                val ny2 = ev.getY(ev.findPointerIndex(mPtrID2))

                rotationInRadians = angleBetweenLines(x2, y2, x1, y1, nx2, ny2, nx1, ny1)

                if (!mStarted) {
                    if (Math.abs(rotationInRadians) > rotationThreshold) {
                        mStarted = true

                        if (delegate!!.shouldBegin(this)) {
                            state = UIGestureRecognizer.State.Began

                            if (null == requireFailureOf) {
                                fireActionEventIfCanRecognizeSimultaneously()
                            } else {
                                if (requireFailureOf!!.state == UIGestureRecognizer.State.Failed) {
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
                } else {
                    if (state == UIGestureRecognizer.State.Began) {
                        if (hasBeganFiringEvents()) {
                            state = UIGestureRecognizer.State.Changed
                            fireActionEvent()
                        }
                    } else if (state == UIGestureRecognizer.State.Changed) {
                        state = UIGestureRecognizer.State.Changed
                        fireActionEvent()
                    }
                }

                if (null != mPreviousEvent) {
                    val diff = Math.max(rotationInRadians, mPreviousAngle) - Math.min(rotationInRadians, mPreviousAngle)
                    val time = ev.eventTime - mPreviousEvent!!.eventTime
                    if (time > 0) {
                        velocity = 1000 / time * diff
                    } else {
                        velocity = 0f
                    }
                    mPreviousEvent!!.recycle()
                }

                x1 = ev.getX(ev.findPointerIndex(mPtrID1))
                y1 = ev.getY(ev.findPointerIndex(mPtrID1))
                x2 = ev.getX(ev.findPointerIndex(mPtrID2))
                y2 = ev.getY(ev.findPointerIndex(mPtrID2))

                mPreviousEvent = MotionEvent.obtain(ev)
                mPreviousAngle = rotationInRadians
            }

            MotionEvent.ACTION_CANCEL -> {
                mPtrID1 = INVALID_POINTER_ID
                mPtrID2 = INVALID_POINTER_ID

                mValid = false
                mStarted = false
                mPreviousAngle = 0f
                velocity = 0f

                if (null != mPreviousEvent) {
                    mPreviousEvent!!.recycle()
                    mPreviousEvent = null
                }

                state = UIGestureRecognizer.State.Cancelled
                setBeginFiringEvents(false)
                mHandler.sendEmptyMessage(MESSAGE_RESET)
            }

            else -> {
            }
        }

        return cancelsTouchesInView
    }

    private fun angleBetweenLines(fX: Float, fY: Float, sX: Float, sY: Float, nfX: Float, nfY: Float, nsX: Float, nsY: Float): Float {
        val angle1 = Math.atan2((fY - sY).toDouble(), (fX - sX).toDouble()).toFloat()
        val angle2 = Math.atan2((nfY - nsY).toDouble(), (nfX - nsX).toDouble()).toFloat()
        return angle1 - angle2
    }

    override fun removeMessages() {
        removeMessages(MESSAGE_RESET)
    }

    companion object {

        private val MESSAGE_RESET = 1

        private val INVALID_POINTER_ID = -1
        private val ROTATION_SLOP = 0.008
    }
}

import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type MutableRefObject,
} from "react";

const UNITY_FRAME_MESSAGE_SOURCE = "unity-webgl";

type UnityMethodName =
  | "SpawnPopupVisitors"
  | "SpawnSinglePopupVisitor"
  | "SetCongestionLevel"
  | "SetPopupStockAvailable";

export interface UnityBridgeHandle {
  isReady: () => boolean;
  sendMessage: (methodName: UnityMethodName, payload: string) => boolean;
  spawnPopupVisitors: (popupStoreIndex: number, count: number, hasStock?: boolean) => boolean;
  spawnSinglePopupVisitor: (popupStoreIndex: number) => boolean;
  setCongestionLevel: (level: number) => boolean;
  setPopupStockAvailable: (hasStock: boolean) => boolean;
}

interface UnityCanvasProps {
  className?: string;
  src?: string;
  iframeRef?: MutableRefObject<HTMLIFrameElement | null>;
  onReady?: () => void;
  onPopupArrival?: (popupStoreIndex: number | null) => void;
}

interface PendingUnityMessage {
  methodName: UnityMethodName;
  payload: string;
}

const UNITY_LOADING_VERSION = "20260327-6";

const UnityCanvas = forwardRef<UnityBridgeHandle, UnityCanvasProps>(function UnityCanvas(
  { className = "", src = "/unity/index.html", iframeRef: externalIframeRef, onReady, onPopupArrival },
  ref,
) {
  const internalIframeRef = useRef<HTMLIFrameElement | null>(null);
  const pendingMessagesRef = useRef<PendingUnityMessage[]>([]);
  const [isReady, setIsReady] = useState(false);
  const resolvedSrc = `${src}${src.includes("?") ? "&" : "?"}v=${UNITY_LOADING_VERSION}`;

  const setIframeRef = (element: HTMLIFrameElement | null) => {
    internalIframeRef.current = element;

    if (externalIframeRef) {
      externalIframeRef.current = element;
    }
  };

  const sendMessage = (methodName: UnityMethodName, payload: string) => {
    const contentWindow = internalIframeRef.current?.contentWindow;

    if (!contentWindow || !isReady) {
      return false;
    }

    contentWindow.postMessage({ type: "unity", method: methodName, payload }, "*");
    return true;
  };

  useImperativeHandle(
    ref,
    () => ({
      isReady: () => isReady,
      sendMessage,
      spawnPopupVisitors: (popupStoreIndex: number, count: number, hasStock = true) =>
        sendMessage("SpawnPopupVisitors", `${popupStoreIndex},${count},${hasStock ? 1 : 0}`) ||
        (pendingMessagesRef.current.push({
          methodName: "SpawnPopupVisitors",
          payload: `${popupStoreIndex},${count},${hasStock ? 1 : 0}`,
        }),
        true),
      spawnSinglePopupVisitor: (popupStoreIndex: number) =>
        sendMessage("SpawnSinglePopupVisitor", String(popupStoreIndex)) ||
        (pendingMessagesRef.current.push({
          methodName: "SpawnSinglePopupVisitor",
          payload: String(popupStoreIndex),
        }),
        true),
      setCongestionLevel: (level: number) =>
        sendMessage("SetCongestionLevel", String(level)),
      setPopupStockAvailable: (hasStock: boolean) =>
        sendMessage("SetPopupStockAvailable", hasStock ? "1" : "0") ||
        (pendingMessagesRef.current.push({
          methodName: "SetPopupStockAvailable",
          payload: hasStock ? "1" : "0",
        }),
        true),
    }),
    [isReady],
  );

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      const currentIframeWindow = internalIframeRef.current?.contentWindow;

      if (currentIframeWindow && event.source !== currentIframeWindow) {
        return;
      }

      const data = event.data as {
        source?: string;
        type?: string;
        popupStoreIndex?: number | string | null;
        payload?: number | string | null;
      } | null;

      if (!data || data.source !== UNITY_FRAME_MESSAGE_SOURCE) {
        return;
      }

      if (data.type === "popup-arrival") {
        const rawPopupStoreIndex = data.popupStoreIndex ?? data.payload ?? null;
        const parsedPopupStoreIndex =
          typeof rawPopupStoreIndex === "number"
            ? rawPopupStoreIndex
            : typeof rawPopupStoreIndex === "string"
              ? Number(rawPopupStoreIndex)
              : NaN;

        onPopupArrival?.(Number.isFinite(parsedPopupStoreIndex) ? parsedPopupStoreIndex : null);
        return;
      }

      if (data.type === "ready") {
        while (pendingMessagesRef.current.length > 0) {
          const nextMessage = pendingMessagesRef.current.shift();

          if (!nextMessage || !sendMessage(nextMessage.methodName, nextMessage.payload)) {
            if (nextMessage) {
              pendingMessagesRef.current.unshift(nextMessage);
            }
            break;
          }
        }
        setIsReady(true);
        onReady?.();
      }
    };

    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [onPopupArrival, onReady]);

  return (
    <div className={className}>
      <iframe
        ref={setIframeRef}
        src={resolvedSrc}
        title="Unity Game"
        className="h-full w-full border-0"
        allow="fullscreen"
        onLoad={() => {
          pendingMessagesRef.current = [];
          setIsReady(false);
        }}
      />
    </div>
  );
});

export default UnityCanvas;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by I330347 on 8/25/2016.
 */
public class HPThreadPool extends AbstractExecutorService {
    private ConcurrentLinkedDeque<Runnable> runableQueue = new ConcurrentLinkedDeque();
    private HashSet<Thread> threadsset = new HashSet<>();
    private ReentrantLock reentrantLock = new ReentrantLock();
    private Condition notEmpty = reentrantLock.newCondition();
    private CyclicBarrier cyclicBarrier;
    private Runnable recycleRunable = () ->{
        while(!Thread.currentThread().isInterrupted()){
            Runnable firstRunable = runableQueue.poll();
            if(firstRunable!=null)
                firstRunable.run();
            else {
                reentrantLock.lock();
                try {
                    notEmpty.await();
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                    System.out.println("Not Empty Be Interrupted");
                    Thread.currentThread().interrupt();
                }finally {
                    reentrantLock.unlock();
                }
            }
        }
    };
    private int coreNum;
    private int MaxNum;
    public HPThreadPool(int c, int m){
        this.coreNum = c;
        this.MaxNum = m;
    }

    @Override
    public void shutdown() {
        boolean falg = true;
        retry:
        while(falg){
            //自旋的方式
            falg = false;
            Iterator<Thread> i = threadsset.iterator();
            while(i.hasNext()){
                if(i.next().getState()!=Thread.State.WAITING) {
                    falg = true;
                    continue retry;
                }
            }
        }
        threadsset.stream().filter(thread -> thread.getState()==Thread.State.WAITING).forEach(thread -> thread.interrupt());
    }

    @Override
    public List<Runnable> shutdownNow() {
        threadsset.stream().forEach(thread -> thread.interrupt());
        ArrayList arrayList = new ArrayList();
        runableQueue.stream().forEach(runnable -> arrayList.add(runnable));
        return arrayList;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        if(command == null)
            return;
        if(threadsset.size()<coreNum){
            Thread newthread = new Thread(command){
                @Override
                public void run(){
                    command.run();
                    recycleRunable.run();
                }
            };

            //ProxyProduce proxyProduce = new ProxyProduce(newthread,recycleRunable);
            //Thread proxyThread =(Thread)proxyProduce.bind();
            threadsset.add(newthread);
            //proxyThread.start();
            newthread.start();
        }else {
            runableQueue.add(command);
            reentrantLock.lock();
            notEmpty.signal();
            reentrantLock.unlock();
        }
    }

    public void printInfo(){
        Iterator<Thread> iterator = threadsset.iterator();
        while (iterator.hasNext()) {
            Thread thread = iterator.next();
            System.out.println(thread.toString()+thread.getState().name());
        }
        System.out.println(runableQueue.size());
    }

    private static class ProxyProduce implements InvocationHandler{
        private Runnable runnable;
        private Object target;
        public ProxyProduce(Object target,Runnable r){
            this.target = target;
            this.runnable = r;
        }
        public Object bind(){
            return Proxy.newProxyInstance(target.getClass().getClassLoader(),target.getClass().getInterfaces(),this);
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(target,args);
            runnable.run();
            return result;
        }
    }
}

package com.eka.middleware.sdk.api;
import com.eka.middleware.sdk.api.outline.*;
import com.sun.jdi.event.EventSet;
import org.apache.commons.lang3.StringUtils;

import javax.management.AttributeList;
import javax.management.openmbean.TabularDataSupport;
import javax.management.relation.RoleList;
import javax.management.relation.RoleUnresolvedList;
import javax.print.attribute.standard.PrinterStateReasons;
import javax.script.Bindings;
import javax.script.SimpleBindings;
import javax.swing.*;
import java.awt.*;
import java.beans.beancontext.BeanContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.security.AuthProvider;
import java.security.Provider;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.jar.Attributes;

public class SyncloopFunctionScanner {

    public static List<ServiceOutline> addClass(Class aClass, boolean restrictSyncloopFunctions) {

        List<ServiceOutline> serviceOutlines = new ArrayList<ServiceOutline>();
        Method[] methods = aClass.getDeclaredMethods();

        for (Method method: methods) {

            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            SyncloopFunction methodExport = method.getAnnotation(SyncloopFunction.class);

            if (null == methodExport && !restrictSyncloopFunctions) {
                continue;
            } else if (null == methodExport) {
                methodExport = new SyncloopFunction() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return null;
                    }

                    @Override
                    public String title() {
                        return "";
                    }

                    @Override
                    public String description() {
                        return "";
                    }

                    @Override
                    public String[] in() {
                        Parameter[] parameters = method.getParameters();
                        String[] strings = new String[method.getParameters().length];
                        for (int i = 0; i < parameters.length ; i++) {
                            strings[i] = parameters[i].getName();
                        }
                        return strings;
                    }

                    @Override
                    public String out() {
                        return "output";
                    }

                };
            }

            //TODO Generic Type, Collection & Primitive Type is pending.

            String[] parametersName = methodExport.in();
            String outputParameterName = methodExport.out();
            String methodName = method.getName();
            Class returnType = method.getReturnType();
            String packageName = method.getDeclaringClass().getPackage().getName();
            String className = method.getDeclaringClass().getName();
            Parameter[] parameters = method.getParameters();
            Class[] parametersTypes = new Class[parameters.length];
            String[] parametersTypesStr = new String[parameters.length];

            for (int i = 0 ; i < parameters.length ; i++) {
                parametersTypes[i] = parameters[i].getType();
                parametersTypesStr[i] = parameters[i].getType().getName();
            }

            DataOutline dataOutline = new DataOutline();
            dataOutline.setAcn(className);
            dataOutline.setFunction(methodName);
            dataOutline.setArguments(parametersName);
            dataOutline.setReturnWrapper(returnType.getName());
            dataOutline.setArgumentsWrapper(parametersTypesStr);
            dataOutline.setOutputArguments(outputParameterName);

            ApiInfoOutline apiInfoOutline = new ApiInfoOutline();
            apiInfoOutline.setTitle(methodExport.title());
            apiInfoOutline.setDescription(methodExport.description());

            LatestOutline latestOutline = new LatestOutline();
            latestOutline.setApi_info(apiInfoOutline);
            latestOutline.setData(dataOutline);

            List<IOOutline> input = new ArrayList<IOOutline>();
            for (int i = 0 ; i < parameters.length ; i++) {
                IOOutline ioOutline = new IOOutline();
                ioOutline.setText(parametersName[i]);
                //ioOutline.setType(parameters[i].getType().getSimpleName().toLowerCase());
                ioOutline.setType(mapTypeToString(parameters[i].getParameterizedType(), true));
                input.add(ioOutline);
            }

            IOOutline in = new IOOutline();
            in.setText("in");
            in.setType("document");
            in.setChildren(input);

            List<IOOutline> inputList = new ArrayList<>();
            inputList.add(in);
            
            latestOutline.setInput(inputList);

            List<IOOutline> output = new ArrayList<IOOutline>();
            IOOutline ioOutline = new IOOutline();
            ioOutline.setText(outputParameterName);
            ioOutline.setType(mapTypeToString(method.getGenericReturnType(), true));
            output.add(ioOutline);

            IOOutline out = new IOOutline();
            out.setText("out");
            out.setType("document");
            out.setChildren(output);

            List<IOOutline>  outputList = new ArrayList<>();
            outputList.add(out);
            
            latestOutline.setOutput(outputList);

            ServiceOutline serviceOutline = new ServiceOutline();
            serviceOutline.setLatest(latestOutline);

            serviceOutlines.add(serviceOutline);
        }

        return serviceOutlines;
    }


    private static String mapTypeToString(Type type, boolean isSimpleType) {
        String dataType = "javaObject";
        if ((type == String.class || type == Character.class
                || type == CharSequence.class || type == char.class) && isSimpleType) {
            dataType = "string";
        } else if ((type == Integer.class || type == int.class || type == short.class) && isSimpleType) {
            dataType = "integer";
        } else if ((type == Double.class || type == Float.class || type == Long.class || type == Number.class ||
                type == double.class || type == float.class || type == long.class) && isSimpleType) {
            dataType = "number";
        } else if (type == Date.class && isSimpleType) {
            dataType = "date";
        } else if ((type == Byte.class || type == byte.class) && isSimpleType) {
            dataType = "byte";
        } else if ((type == Boolean.class || type == boolean.class) && isSimpleType) {
            dataType = "boolean";
        } else if (MAP_CLASSES.contains(type) && isSimpleType) {
            dataType = "document";
        } else if (type instanceof Class && ((Class<?>) type).isArray()) {
            Class<?> arrayType = ((Class<?>) type).getComponentType();
            dataType = mapTypeToString(arrayType, true) + "List";
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
            if (COLLECTION_CLASSES.contains(rawType)) {
                dataType = mapTypeToString(actualTypeArgument, true) + "List";
            } else if (MAP_CLASSES.contains(rawType)) {
                dataType = mapTypeToString(rawType, true);
            }
        }
        if (dataType.contains("ListList")) {
            dataType = dataType.replaceAll("List", "") + "List";
        }
        return dataType;
    }


    private static final Set<Class> MAP_CLASSES = getMapClasses();
    
    
    private static Set<Class> getMapClasses(){
    	Set<Class> MAP_CLASSES = new HashSet<>();
    	Collections.addAll(MAP_CLASSES,AbstractMap.class,
                Attributes.class, AuthProvider.class,
                ConcurrentHashMap.class,
                ConcurrentSkipListMap.class, Map.class,
                EnumMap.class,
                HashMap.class,
                com.eka.lite.heap.HashMap.class,
                Hashtable.class, IdentityHashMap.class, LinkedHashMap.class,
                PrinterStateReasons.class, Properties.class, Provider.class,
                RenderingHints.class, SimpleBindings.class, TabularDataSupport.class, TreeMap.class, UIDefaults.class, WeakHashMap.class,
                Bindings.class, ConcurrentMap.class, ConcurrentNavigableMap.class, NavigableMap.class, SortedMap.class);
    	return MAP_CLASSES;
    }

    private static final Set<Class> COLLECTION_CLASSES = getCollectionClasses();

    
    private static Set<Class> getCollectionClasses(){
    	Set<Class> COLLECTION_CLASSES= new HashSet<>();
    			Collections.addAll(COLLECTION_CLASSES,
                BlockingDeque.class, BlockingQueue.class, Deque.class,
                EventSet.class, List.class, NavigableSet.class, Queue.class, Set.class,
                SortedSet.class, TransferQueue.class,
                AbstractCollection.class,
                AbstractList.class, AbstractQueue.class, AbstractSequentialList.class, AbstractSet.class,
                ArrayBlockingQueue.class, ArrayDeque.class, ArrayList.class, AttributeList.class,
                ConcurrentHashMap.KeySetView.class,
                ConcurrentLinkedDeque.class, ConcurrentLinkedQueue.class, ConcurrentSkipListSet.class, CopyOnWriteArrayList.class, CopyOnWriteArraySet.class,
                DelayQueue.class, EnumSet.class, HashSet.class, LinkedBlockingDeque.class,
                LinkedBlockingQueue.class, LinkedHashSet.class, LinkedList.class, LinkedTransferQueue.class,
                PriorityBlockingQueue.class, PriorityQueue.class, RoleList.class,
                RoleUnresolvedList.class,
                Stack.class, SynchronousQueue.class, TreeSet.class, Vector.class
            );
    	return COLLECTION_CLASSES;
    }
    
    public static final Map<String, Class> PRIMITIVE_TYPE = new HashMap<String, Class>();

    static {
        PRIMITIVE_TYPE.put("int", int.class);
        PRIMITIVE_TYPE.put("short", short.class);
        PRIMITIVE_TYPE.put("void", void.class);
        PRIMITIVE_TYPE.put("double", double.class);
        PRIMITIVE_TYPE.put("float", float.class);
        PRIMITIVE_TYPE.put("long", long.class);
        PRIMITIVE_TYPE.put("boolean", boolean.class);
        PRIMITIVE_TYPE.put("char", char.class);
    }
}
